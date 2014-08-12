package eu.chepy.audiokit.ui.utils.uil;

import com.nostra13.universalimageloader.cache.disc.naming.Md5FileNameGenerator;

/**
 * Created by phil on 12/08/14.
 */
public class PrefixedFileNameGenerator extends Md5FileNameGenerator {

    private String filePrefix;

    public PrefixedFileNameGenerator(String prefix) {
        filePrefix = prefix;
    }

    @Override
    public String generate(String imageUri) {
        return filePrefix + super.generate(imageUri);
    }
}
