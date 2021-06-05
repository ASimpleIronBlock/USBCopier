package me.ironblock.usbcopier.main;

import com.google.common.collect.Lists;
import me.ironblock.usbcopier.config.ConfigHelper;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.filechooser.FileSystemView;
import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Main {
    //Copy Destination
    public static File copyDestination;
    public static final Logger logger = LogManager.getLogger("Main");
    private final FileSystemView view = FileSystemView.getFileSystemView();
    private final List<File> rootList = Lists.newArrayList();
    private final ConfigHelper config = new ConfigHelper();


    private final List<CompletableFuture<Void>> threads = new ArrayList<>();

    public static void main(String[] args) {
        try {
            new Main().run();
        } catch (Exception e) {
            logger.fatal("Exception",e);
        }
    }

    /**
     * Run
     */
    public void run(){
        config.init();
        copyDestination = config.getOutputDir();
        while (true){
            checkHardDrive();
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Check if there is a new hard drive
     */
    private void checkHardDrive(){
        File[] roots = File.listRoots();
        for (File root : roots) {
            if (!rootList.contains(root)){
                onUSBIn(root);
            }
        }
        rootList.clear();
        rootList.addAll(Lists.newArrayList(roots));

    }

    /**
     * Triggered by the method checkHardDrive(), this method check if the new hard drive is a USB.If yes,prepare to copy files.
     * @param usb the new hard disk's identifier
     */
    private void onUSBIn(File usb){
        String usbName = view.getSystemDisplayName(usb);
        String usbNameWithoutDiskIdentifier = usbName.split(" ")[0];
        logger.info("Detected Disk in :"+usbName);
        logger.info("USB type:"+view.getSystemTypeDescription(usb));
        if (view.getSystemTypeDescription(usb).equals(config.getExternal_HardDrive_String())){
            logger.info("Detected USB in");
            if ((config.getListMode()==2&&config.getWhiteList().contains(usbNameWithoutDiskIdentifier))||(config.getListMode()==1&& !config.getBlackList().contains(usbNameWithoutDiskIdentifier))||config.getListMode()==0){
                copy(usb,copyDestination);
            }else{
                logger.info("The USB will not be copied. USBName:"+usbNameWithoutDiskIdentifier);
            }
        }


    }

    /**
     * Launch the copy threads and wait them to complete
     * @param usb the file of the usb
     * @param des copy destination
     * @param usbName the name of the usb
     */
    private void launchCopyThread(File usb,File des,String usbName){
        List<File> fileList = Arrays.asList(Objects.requireNonNull(usb.listFiles()));
        AtomicInteger current = new AtomicInteger(fileList.size());
        logger.info(fileList.size());
        final ReadWriteLock lock = new ReentrantReadWriteLock();
        for (int i = 0; i < config.getCopyThreads(); i++) {
            threads.add(CompletableFuture.supplyAsync(()->{
                while (current.get()>0) {
                    lock.writeLock().lock();
                    logger.info("get lock");
                    File file = fileList.get(current.decrementAndGet());
                    logger.info("unlock");
                    lock.writeLock().unlock();
                    logger.info("Start copying files from "+file+" to "+new File(des,usbName+"/"+file.getName()));
                    copyFiles(file,new File(des,usbName+"/"+file.getName()),usbName);
                    logger.info("copied");
                }
                logger.info(Thread.currentThread()+" completed");
                return null;
            }));
            logger.info("Launched Thread "+i);
        }
        try {
            CompletableFuture.allOf(threads.toArray(new CompletableFuture[0])).get();
        } catch (InterruptedException|ExecutionException e) {
            logger.error("",e);
        }
        logger.info(Thread.currentThread()+" copy completed");
    }

    /**
     * Copy files,create folders where files and md5 files will be put
     * @param src Directory or file will be copied
     * @param des Destination
     */
    private void copy(File src,File des){
        String usbName = view.getSystemDisplayName(src).split(" ")[0];
        File file = new File(des,usbName);
        if (!file.exists()){
            logger.info("Created folder automatically: "+file);
            file.mkdirs();
        }
        File md5Folder = new File(des,"md5/"+usbName);
        if (!md5Folder.exists()){
            logger.info("Created folder automatically: "+md5Folder);
            md5Folder.mkdirs();
        }
        launchCopyThread(src,des,usbName);

    }

    /**
     * Copy files recursively
     * @param src Source file
     * @param des destination
     * @param usbName name of the USB
     */
    private void copyFiles(File src,File des,String usbName){

        if (src.isDirectory()){
            File[] fileList = src.listFiles();
            if (fileList==null){
                logger.fatal("A error occurred when reading "+src);
            }else{
                for (File file: fileList){
                    copyFiles(file,new File(des,file.getName()),usbName);
                }
            }
        }else if(src.isFile()){
            if (copyOrNot(src, usbName)){
                try {
                    logger.info("Attempting to copy file from "+src+" to "+des);
                    des.getParentFile().mkdirs();
                    FileInputStream fileInputStream = new FileInputStream(src);
                    IOUtils.copy(fileInputStream,new FileOutputStream(des));
                    fileInputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }else{
                logger.info("didn't copy file due to some reason.");
            }
        }

    }

    /**
     * Calculate MD5
     * @param bytes The byte array of the file
     * @return md5 calculated
     */
    private byte[] calcMD5(byte[] bytes){
        try {
            MessageDigest md5Calculator = MessageDigest.getInstance("MD5");
            md5Calculator.update(bytes);
            return md5Calculator.digest();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            logger.fatal("An error encountered occurred while calculating md5.");
        }
        return new byte[0];
    }

    /**
     * Judge if the file need to be copied according to its md5,size,etc.
     * @param src file
     * @param usbName name of the USB
     * @return need to be copied
     */
    private boolean copyOrNot(File src,String usbName){
        try {
            if(src.exists()){
                //Stop copying files larger than config
                if (src.length()> config.getMaxFileSize()*1024){
                    logger.info(src+" is too large ("+src.length()+"/"+config.getMaxFileSize()*1024+")");
                    return false;
                }
                //suffixes
                if (config.isUseSuffix()){
                    String[] tmp = src.getName().split("\\.");
                    if (!config.getSuffixes().contains(tmp[tmp.length - 1])){
                        logger.info(src+" doesn't have the specified suffix");
                        return false;
                    }

                }

                //md5
                FileInputStream stream = new FileInputStream(src);
                byte[] nowMD5 = calcMD5(IOUtils.toByteArray(stream));
                int first = src.getAbsolutePath().indexOf('\\');
                String filePath = src.getAbsolutePath().substring(first);
                File md5File = new File(copyDestination,"/md5/"+usbName+filePath+".md5");
                logger.info("Attempting to find md5 value for "+src +" at "+md5File);
                stream.close();
                if (md5File.exists()){
                    byte[] oldMD5 = IOUtils.toByteArray(new FileInputStream(md5File));
                    boolean md5Equals = Arrays.equals(oldMD5, nowMD5);
                    logger.info(src+"'s md5 in disk is "+ Arrays.toString(oldMD5) + ",new md5 is "+ Arrays.toString(nowMD5),",so "+(md5Equals?"don't copy":"copy"));
                    return !md5Equals;
                }else{
                    //保存md5
                    md5File.getParentFile().mkdirs();
                    IOUtils.write(nowMD5,new FileOutputStream(md5File));
                    return true;
                }


            }

        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;

    }


}
