package com.emerald.backend.content;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
class ContentKeysTest {
 @Test void windowsLineEndingsAreAccepted() {
  assertEquals(Set.of("hero.line1", "faq.1.answer"), AdminContentController.parseKeys("hero.line1\r\nfaq.1.answer\r\n"));
 }
 @Test void unixAndWindowsKeysMatch() {
  assertEquals(AdminContentController.parseKeys("hero.line1\nfaq.1.answer\n"), AdminContentController.parseKeys("hero.line1\r\nfaq.1.answer\r\n"));
 }
}
