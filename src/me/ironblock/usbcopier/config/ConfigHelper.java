package me.ironblock.usbcopier.config;

import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

public class ConfigHelper {
    private static final Logger LOGGER = LogManager.getLogger("Config");

    private static final File configFile = new File("configs.properties");
    private static final File suffixFile = new File("extensions.txt");
    private static final File whiteListUSB = new File("whiteListUSB.txt");
    private static final File blackListUSB = new File("blackListUSB.txt");
    private int listMode;   //0->none,1->whiteList,2->blackList
    private List<String> suffixes, blackList, whiteList;
    private boolean useSuffix;
    private long maxFileSize;
    private File outputDir;
    private int copyThreads;
    private String External_HardDrive_String;

    public void init() {
        try {
            Properties properties = new Properties();
            if (!configFile.exists()) {
                createConfigFiles(properties);
            } else {
                properties.load(new FileInputStream(configFile));
            }
            String tmp = properties.getProperty("lists");
            if (tmp.equals("none")) {
                listMode = 0;
            } else if (tmp.equals("blackList")) {
                listMode = 1;
            } else if (tmp.equals("whiteList")) {
                listMode = 2;
            } else {
                LOGGER.error("Property \"lists\" can only be \"blackList/whiteList/none\",setting mode to none");
            }

            try (InputStream suffix = new FileInputStream(suffixFile)) {
                this.suffixes = Arrays.asList(parseList(new String(IOUtils.toByteArray(suffix))));
            }
            try (InputStream whiteList = new FileInputStream(whiteListUSB)) {
                this.whiteList = Arrays.asList(parseList(new String(IOUtils.toByteArray(whiteList))));
            }
            try (InputStream blackList = new FileInputStream(blackListUSB)) {
                this.blackList = Arrays.asList(parseList(new String(IOUtils.toByteArray(blackList))));
            }

            try {
                useSuffix = Boolean.parseBoolean(properties.getProperty("useFileNameExtension"));
            } catch (Exception e) {
                LOGGER.error("Property \nuseFileNameExtension\n can only be true/false",e);
            }
            try {
                maxFileSize = Long.parseLong(properties.getProperty("fileMaxSize"));
            } catch (NumberFormatException e) {
                LOGGER.error("Property \nfileMaxSize\n can only be a number",e);
            }

            outputDir = new File(properties.getProperty("OutputDirectory"));
            if (!outputDir.exists()){
                outputDir.mkdirs();
            }
            try {
                copyThreads = Integer.parseInt(properties.getProperty("CopyThreads"));
            } catch (NumberFormatException e) {
                LOGGER.error("Property \"Copy Threads\" can only be a number");
                copyThreads = 2;
            }

            External_HardDrive_String = properties.getProperty("External_HardDrive_String");


        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private void createConfigFiles(Properties properties) throws IOException {
        //create properties
        configFile.createNewFile();
        properties.put("fileMaxSize", "100");
        properties.put("useFileNameExtension", "false");
        properties.put("lists", "none");
        properties.put("OutputDirectory", "D:/USBCopier/");
        properties.put("CopyThreads", "2");
        properties.put("External_HardDrive_String", "可移动磁盘");
        properties.store(new FileOutputStream(configFile), "fileMaxSize: Files larger than this number will not be copied.(KB)\n" + "useFileNameExtension: If true,only files whose extension is included in the suffixFile will be copied\n" + "lists: [blackList/whiteList/none] \n" + "   blackList: USBs included in the blackListUSB will not be copied." + "   whiteList: Only USBs included in the whiteListUSB will be copied" + "   none: All USBs will be copied.");
        suffixFile.createNewFile();
        whiteListUSB.createNewFile();
        blackListUSB.createNewFile();



    }

    private String[] parseList(String originString) {
        return originString.split(",");
    }

    public int getListMode() {
        return listMode;
    }

    public List<String> getSuffixes() {
        return suffixes;
    }

    public List<String> getBlackList() {
        return blackList;
    }

    public List<String> getWhiteList() {
        return whiteList;
    }

    public boolean isUseSuffix() {
        return useSuffix;
    }

    public long getMaxFileSize() {
        return maxFileSize;
    }

    public File getOutputDir() {
        return outputDir;
    }

    public int getCopyThreads() {
        return copyThreads;
    }

    public String getExternal_HardDrive_String() {
        return External_HardDrive_String;
    }
}
