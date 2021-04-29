package me.ironblock.usbcopier.main;

import com.google.common.collect.Lists;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.filechooser.FileSystemView;
import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class Main {
    //复制到的位置
    public static final File copyDestination = new File("D:/usbCopier/");
    public static final Logger logger = LogManager.getLogger("Main");

    private final FileSystemView view = FileSystemView.getFileSystemView();
    private final List<File> rootList = Lists.newArrayList();

    public static void main(String[] args) {

        new Main().run();
    }

    /**
     * 运行
     */
    public void run(){
        while (true){
            checkUSB();
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 检查是否有新的文件设备接入
     */
    private void checkUSB(){
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
     * 由checkUSB()方法触发,这个方法检测接入的是不是U盘,如果是,就开始准备复制文件
     * @param usb 接入设备的盘符
     */
    private void onUSBIn(File usb){
        logger.error("Detected USB in :"+view.getSystemDisplayName(usb));
        logger.error("USB type:"+view.getSystemTypeDescription(usb));
        System.out.println();
        if (view.getSystemTypeDescription(usb).equals("可移动磁盘")){
            logger.error("检测到了插入电脑的U盘");
            new Thread(()->copy(usb,copyDestination)).start();
        }
    }

    /**
     * 复制文件
     * 创建文件的文件夹和md5的文件夹
     * @param src 源文件
     * @param des 目标路径
     */
    private void copy(File src,File des){
        String usbName = view.getSystemDisplayName(src).split(" ")[0];
        File file = new File(des,usbName);
        if (!file.exists()){
            System.out.println(file);
            logger.error("自动创建了文件夹: "+file);
            file.mkdirs();
        }
        File md5Folder = new File(des,"md5/"+usbName);
        if (!md5Folder.exists()){
            System.out.println(md5Folder);
            logger.error("自动创建了文件夹: "+md5Folder);
            md5Folder.mkdirs();
        }
        copyFiles(src,new File(des,usbName),usbName);
        logger.error("复制完成");
    }

    /**
     * 递归的复制文件
     * @param src 源文件
     * @param des 目标路径
     * @param usbName U盘的名字
     */
    private void copyFiles(File src,File des,String usbName){

        if (src.isDirectory()){
            for (File file: Objects.requireNonNull(src.listFiles())){
                copyFiles(file,new File(des,file.getName()),usbName);
            }
        }else if(src.isFile()){
            if (copyOrNot(src, usbName)){
                try {
                    logger.error("尝试把文件从 "+src+" 复制到 "+des);
                    des.getParentFile().mkdirs();
                    IOUtils.copy(new FileInputStream(src),new FileOutputStream(des));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }else{
                logger.error("文件 "+src+" 和硬盘里的文件有相同的md5,所以没有复制");
            }
        }

    }

    /**
     * 计算md5
     * @param bytes 文件的bytes
     * @return 计算出的md5
     */
    private byte[] calcMD5(byte[] bytes){
        try {
            MessageDigest md5Calculator = MessageDigest.getInstance("MD5");
            md5Calculator.update(bytes);
            return md5Calculator.digest();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            logger.fatal("在计算md5时候出现了错误");
        }
        return new byte[0];
    }

    /**
     * 根据文件的大小,md5等因素判断文件是否需要复制
     * @param src 源文件
     * @param usbName U盘的名称
     * @return 是否需要复制
     */
    private boolean copyOrNot(File src,String usbName){
        try {
            if(src.exists()){
                //不复制大于100M的文件
                if (src.length()>100_000_000L){
                    return false;
                }
                byte[] nowMD5 = calcMD5(IOUtils.toByteArray(new FileInputStream(src)));
                int first = src.getAbsolutePath().indexOf('\\');
                String filePath = src.getAbsolutePath().substring(first);
                File md5File = new File(copyDestination,"/md5/"+usbName+filePath+".md5");
                logger.error("尝试在 "+md5File+" 为 "+src +" 寻找MD5的值");
                if (md5File.exists()){
                    byte[] oldMD5 = IOUtils.toByteArray(new FileInputStream(md5File));
                    return !Arrays.equals(oldMD5, nowMD5);
                }else{
                    md5File.getParentFile().mkdirs();
                    IOUtils.write(nowMD5,new FileOutputStream(md5File));
                    return true;
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
        return true;

    }

}
