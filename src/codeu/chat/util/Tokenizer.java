package codeu.chat.util;

import com.sun.xml.internal.ws.policy.privateutil.PolicyUtils;

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
        token = new StringBuilder();
    }

    /**
     * Tokenizes the source String by turning each group of characters
     * separated by white space into a String. Returns the next group of
     * characters in the source String for every method call.
     *
     * @return  String      Next group of characters without white space in the source String.
     * @throws IOException  May throw an IO Exception through calling the read() method.
     */
    public String next() throws IOException {
        //ignores all whitespace at beginning of the source String
        while (remaining() > 0 && Character.isWhitespace(peek())){
            read();
        }
        //returns null if there are no more characters in the String
        if (remaining() <= 0 ){
            return null;
        }
        // if the source String is surrounded by quotes
        else if (peek() == '"'){
           return readWithQuotes();
        }
        else {
           return readWithNoQuotes();
        }
    }

    /**
     * Returns the next group of characters in the source String
     * surrounded by quotes for every method call.
     *
     * @return  String      Next group of characters without white space in the source String.
     * @throws IOException  May throw an IO Exception through calling the read() method or if
     *                      String does not have a leading quotation mark.
     */
    private String readWithQuotes() throws IOException {
        //clearing StringBuilder that will hold returned String
        token.setLength(0);
        //ensures first character is quotation, also allows next non-quote character to be read next
        if (read() != '"'){
            throw new IOException("String is not surrounded by quotes!");
        }
        //appends characters until a closing quotations mark is reached
        while (remaining() > 0 && peek() != '"'){
            token.append(read());
        }
        //reads the closing quotation mark
        read();
        return token.toString();
    }

    /**
     * Returns the next group of characters in the source String
     * with no surrounding quotes for every method call.
     *
     * @return  String      Next group of characters without white space in the source String.
     * @throws IOException  May throw an IO Exception through calling the read() method.
     */
    private String readWithNoQuotes() throws IOException {
        //clearing StringBuilder that will hold returned String
        token.setLength(0);
        //appends characters until a white space is reached
        while (remaining() > 0 && !Character.isWhitespace(peek())){
            token.append(read());
        }
        return token.toString();
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
     * Reads the character at the index of the at pointer
     * in the source String (if available). Uses the peek
     * method to get the current character and increments
     * at to point to the next index.
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
