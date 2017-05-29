package codeu.chat.util;

import java.io.IOException;
import static org.junit.Assert.*;
import org.junit.Test;

/**
 * Created by Jiahui Chen on 5/24/2017.
 */
public class TokenizerTest {
    @Test
    public void testWithNoQuotes() throws IOException{
        final Tokenizer noQuotesSimple = new Tokenizer("1 two 3 4");
        assertEquals("1", noQuotesSimple.next());
        assertEquals("two", noQuotesSimple.next());
        assertEquals("3", noQuotesSimple.next());
        assertEquals("4", noQuotesSimple.next());
        assertEquals(null, noQuotesSimple.next());

        final Tokenizer noQuotesEmpty = new Tokenizer("");
        assertEquals(null, noQuotesEmpty.next());
    }

    @Test
    public void testWithQuotes() throws IOException{
        final Tokenizer withQuotesSimple = new Tokenizer("\"hello world\" \"how are you\"");
        assertEquals(withQuotesSimple.next(),"hello world");
        assertEquals(withQuotesSimple.next(), "how are you");
        assertEquals(withQuotesSimple.next(), null);

        final Tokenizer withQuotesWeirdSpacing = new Tokenizer("\" hello there \" \"  good day\"");
        assertEquals(" hello there ", withQuotesWeirdSpacing.next());
        assertEquals("  good day", withQuotesWeirdSpacing.next());
    }
}
