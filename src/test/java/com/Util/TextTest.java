package com.Util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Behaviour of the display-text pipeline.
 *
 * <p>Runs without a server: {@link Text} treats an absent plugin instance as {@code Formatting.Mode:
 * auto} with ampersand translation on, which is the shipped default. That makes the interesting part
 * — what MiniMessage, {@code &} codes and legacy {@code §} codes each turn into — directly
 * testable.</p>
 */
class TextTest {
   private static final char S = '\u00a7';

   @BeforeEach
   void freshCache() {
      Text.clearCache();
   }

   @Test
   @DisplayName("legacy-only text is returned untouched")
   void legacyTextIsUntouched() {
      String legacy = S + "8[" + S + "6错误庄园" + S + "8] " + S + "a已创建";
      assertEquals(legacy, Text.format(legacy));
   }

   @Test
   @DisplayName("null and empty input are passed through")
   void emptyInputIsPassedThrough() {
      assertEquals(null, Text.format((String)null));
      assertEquals("", Text.format(""));
      assertEquals(null, Text.format((List<String>)null));
   }

   @Test
   @DisplayName("ampersand colour codes become section codes")
   void ampersandCodesAreTranslated() {
      assertEquals(S + "a绿色" + S + "l粗体", Text.format("&a绿色&l粗体"));
   }

   @Test
   @DisplayName("&#RRGGBB expands to the repeated-character hex form clients understand")
   void ampersandHexIsExpanded() {
      String expected = S + "x" + S + "f" + S + "f" + S + "5" + S + "f" + S + "6" + S + "d" + "红";
      assertEquals(expected, Text.format("&#ff5f6d红"));
   }

   @Test
   @DisplayName("a malformed &# sequence is left alone rather than eating characters")
   void malformedHexIsLeftAlone() {
      assertEquals("&#zzzzzz文本", Text.format("&#zzzzzz文本"));
      assertEquals("&#ff5", Text.format("&#ff5"));
   }

   @Test
   @DisplayName("MiniMessage colour and decoration tags are resolved")
   void miniMessageTagsAreResolved() {
      String rendered = Text.format("<green>绿色</green>");
      assertEquals(S + "a绿色", rendered);
      assertTrue(Text.format("<bold>粗体").startsWith(S + "l"), "expected a bold section code");
   }

   @Test
   @DisplayName("MiniMessage hex and gradients produce hex section codes")
   void miniMessageHexIsResolved() {
      assertTrue(Text.format("<#ff5f6d>红").contains(S + "x"), "expected hex output for <#ff5f6d>");
      String gradient = Text.format("<gradient:#ff5f6d:#ffc371>错误庄园</gradient>");
      assertTrue(gradient.contains(S + "x"), "expected hex output across a gradient");
      assertTrue(gradient.contains("错"), "gradient must keep its content");
   }

   @Test
   @DisplayName("a broken MiniMessage tag degrades to the unparsed text instead of blanking the line")
   void brokenTagDegradesGracefully() {
      String broken = "<gradient:#ff5f6d>未闭合";
      String rendered = Text.format(broken);
      assertTrue(rendered.contains("未闭合"), "content must survive a parse failure: " + rendered);
   }

   @Test
   @DisplayName("tag detection only fires on something that is actually shaped like a tag")
   void tagDetectionIsConservative() {
      assertTrue(Text.looksLikeMiniMessage("<red>x"));
      assertTrue(Text.looksLikeMiniMessage("</red>"));
      assertTrue(Text.looksLikeMiniMessage("<#ff0000>x"));
      assertFalse(Text.looksLikeMiniMessage("等级 < 5"));
      assertFalse(Text.looksLikeMiniMessage("a < b > c"));
      assertFalse(Text.looksLikeMiniMessage("没有尖括号"));
      assertFalse(Text.looksLikeMiniMessage(null));
   }

   @Test
   @DisplayName("placeholder markers used by the language files are not mistaken for tags")
   void placeholderMarkersAreNotTags() {
      // The language files are full of <Name>, <player>, <Key>, <NeedPoints> and friends. They do
      // look like tags, so they must survive MiniMessage parsing as literal text.
      for (String raw : List.of("<Name>", "<player>", "<Key>", "<NeedPoints>", "<Mode>", "<type>")) {
         assertEquals(raw, Text.format(raw), "placeholder marker must be preserved: " + raw);
      }
   }

   @Test
   @DisplayName("legacy codes survive alongside MiniMessage tags in one string")
   void legacyAndMiniMessageCoexist() {
      String rendered = Text.format("<green>绿" + S + "c红");
      assertTrue(rendered.contains("绿"), "MiniMessage part must render: " + rendered);
      assertTrue(rendered.contains(S + "c红") || rendered.contains("红"), "legacy part must survive: " + rendered);
   }

   @Test
   @DisplayName("lists are formatted line by line")
   void listsAreFormatted() {
      List<String> rendered = Text.format(List.of("&a一", "<green>二"));
      assertEquals(2, rendered.size());
      assertEquals(S + "a一", rendered.get(0));
      assertEquals(S + "a二", rendered.get(1));
   }

   @Test
   @DisplayName("an empty list is returned as-is rather than copied")
   void emptyListIsReturnedAsIs() {
      List<String> empty = List.of();
      assertSame(empty, Text.format(empty));
   }

   @Test
   @DisplayName("repeated formatting of the same string is stable")
   void formattingIsIdempotentAcrossCalls() {
      String raw = "<gradient:#ff5f6d:#ffc371>错误庄园</gradient>";
      assertEquals(Text.format(raw), Text.format(raw));
   }
}
