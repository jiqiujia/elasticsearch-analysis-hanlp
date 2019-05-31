package com.hankcs.dic;

import com.hankcs.dic.cache.DictionaryFileCache;
import com.hankcs.hanlp.HanLP;
import com.hankcs.hanlp.dictionary.CustomDictionary;
import com.hankcs.hanlp.utility.Predefine;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.common.logging.ESLoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/**
 * Created with IntelliJ IDEA.
 * User: Kenn
 * Date: 2018/2/8
 * Time: 16:03
 * Project: elasticsearch-analysis-hanlp
 * Description:
 */
public class Monitor implements Runnable {

    private static final Logger logger = ESLoggerFactory.getLogger(Monitor.class);

    @Override
    public void run() {
        HanLP.Config.enableDebug();
        List<DictionaryFile> originalDictionaryFileList = DictionaryFileCache.getCustomDictionaryFileList();
        logger.debug("hanlp original custom dictionary: {}", Arrays.toString(originalDictionaryFileList.toArray()));
        reloadProperty();
        List<DictionaryFile> currentDictironaryFileList = getCurrentDictionaryFileList(HanLP.Config.CustomDictionaryPath);
        logger.debug("hanlp current custom dictionary: {}", Arrays.toString(currentDictironaryFileList.toArray()));
        boolean isModified = false;
        for (DictionaryFile currentDictionaryFile : currentDictironaryFileList) {
            if (!originalDictionaryFileList.contains(currentDictionaryFile)) {
                isModified = true;
                break;
            }
        }
        if (isModified) {
            logger.info("reloading hanlp custom dictionary");
            try {
                CustomDictionary.reload();
            } catch (Exception e) {
                logger.error("can not reload hanlp custom dictionary", e);
            }
            DictionaryFileCache.setCustomDictionaryFileList(currentDictironaryFileList);
            logger.info("finish reload hanlp custom dictionary");
        } else {
            logger.debug("hanlp custom dictionary isn't modified, so no need reload");
        }
    }

    private void reloadProperty() {
        Properties p = new Properties();
        try {
            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            if (loader == null) {  // IKVM (v.0.44.0.5) doesn't set context classloader
                loader = HanLP.Config.class.getClassLoader();
            }
            p.load(new InputStreamReader(Predefine.HANLP_PROPERTIES_PATH == null ? loader.getResourceAsStream("hanlp.properties") : new FileInputStream(Predefine.HANLP_PROPERTIES_PATH), "UTF-8"));
            String root = p.getProperty("root", "").replaceAll("\\\\", "/");
            if (root.length() > 0 && !root.endsWith("/")) root += "/";
            String[] pathArray = p.getProperty("CustomDictionaryPath", "data/dictionary/custom/CustomDictionary.txt").split(";");
            String prePath = root;
            for (int i = 0; i < pathArray.length; ++i) {
                if (pathArray[i].startsWith(" ")) {
                    pathArray[i] = prePath + pathArray[i].trim();
                } else {
                    pathArray[i] = root + pathArray[i];
                    int lastSplash = pathArray[i].lastIndexOf('/');
                    if (lastSplash != -1) {
                        prePath = pathArray[i].substring(0, lastSplash + 1);
                    }
                }
            }
            HanLP.Config.CustomDictionaryPath = pathArray;
        } catch (Exception e) {
            logger.error("can not find hanlp.properties", e);
        }
    }

    private List<DictionaryFile> getCurrentDictionaryFileList(String[] customDictionaryPaths) {
        List<DictionaryFile> dictionaryFileList = new ArrayList<>();
        for (String customDictionaryPath : customDictionaryPaths) {
            File file = new File(customDictionaryPath);
            if (file.exists()) {
                dictionaryFileList.add(new DictionaryFile(customDictionaryPath, file.lastModified()));
            }
        }
        return dictionaryFileList;
    }
}
