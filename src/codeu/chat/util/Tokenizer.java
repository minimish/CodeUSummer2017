package codeu.chat.util;

import java.io.IOException;

public final class Tokenizer {

    //stores the final "token" form of the input
    private StringBuilder token;

    //the input being processed/tokenized
    private String source;

    //index where current character being read is located
    private int at;

    public Tokenizer(String source) {
        this.source = source;
        at = 0;
    }

    public String next() throws IOException {
        //ignores all whitespace at beginning of the string
        while (remaining() > 0 && Character.isWhitespace(peek())){
            read();
        }
        //returns null if there are no more characters in the String
        if (remaining() <= 0 ){
            return null;
        }
        // if the source String is surrounded by quotes
        else if (peek() == '"'){

        }
        else {

        }
        return "";
    }

    /**
     * Calculates how many characters in the source String
     * still need to be read.
     *
     * @return int indicating number of characters that still need to be read.
     */
    private int remaining() {
        return source.length() - at;
    }

    /**
     * Returns the character in the source String at
     * the index indicated by the "at" field variable.
     *
     * @return               Character in source String at the index indicated
     *                       by value of "at" variable.
     * @throws IOException   If the index indicated is greater than String lenght
     *                       an IO Exception is thrown.
     */
    private char peek() throws IOException {
        if (at < source.length()){
            return source.charAt(at);
        }
        else {
            throw new IOException("No more characters!");
        }
    }

    /**
     * Reads the next character (if available) in the source String
     * using the peek method to get the next character.
     *
     * @return  char            Next character in source String.
     * @throws  IOException     If there is no next character.
     */
    private char read() throws IOException {
        final char next = peek();
        at ++;
        return next;
    }

}
