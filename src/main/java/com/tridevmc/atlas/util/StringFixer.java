package com.tridevmc.atlas.util;

public class StringFixer {
    private StringFixer() {}


    public static String dotsToSlash(String str) {
        return str.replace(".", "/");
    }

}
