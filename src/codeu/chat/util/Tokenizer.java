package codeu.chat.util;

import com.sun.xml.internal.ws.policy.privateutil.PolicyUtils;

import java.io.IOException;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.Function;

//comment to test the first commit to the persistent_storage branch
public final class  Tokenizer implements Iterator<String> {

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
     * Iterator interface's method. Uses remaining() method
     * to see if there's characters left to read.
     *
     * @return  boolean     True or false indicating if there's characters left.
     */
    @Override
    public boolean hasNext() {
        if (remaining() > 0){
            return true;
        }
        else {
            return false;
        }
    }

    /**
     * Tokenizes the source String by turning each group of characters
     * separated by white space into a String. Returns the next group of
     * characters in the source String for every method call.
     *
     * @return  String      Next group of characters without white space in the source String.
     * @throws IOException  May throw an IO Exception through calling the read() method.
     */
    @Override
    public String next() throws NoSuchElementException {
        try {
            //ignores all whitespace at beginning of the source String
            while (hasNext() && Character.isWhitespace(peek())) {
                read();
            }
            //returns null if there are no more characters in the String
            if (!hasNext()) {
                return null;
            }
            // if the source String is surrounded by quotes
            else if (peek() == '"') {
                //reads in leading quotation
                read();
                //characters read until the ending quotation is read
                readUntil(c -> c == '"');
                //reading in final quotation
                read();
            } else {
                //characters read until white space is read
                readUntil(c -> Character.isWhitespace(c));
            }
            return token.toString();
        } catch (IOException e) {
            throw new NoSuchElementException("No more characters!");
        }
    }

    /**
     * Returns the next group of characters in the source String
     * for every method call, handles source String with and without quotation marks.
     *
     * @return  String      Next group of characters without white space in the source String.
     * @throws IOException  May throw an IO Exception through calling the read() method or if
     *                      String does not have a leading quotation mark.
     */
    private String readUntil(Function<Character, Boolean> terminationCondition) throws IOException{
        token.setLength(0);
        while (hasNext() && terminationCondition.apply(peek()) == false){
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
