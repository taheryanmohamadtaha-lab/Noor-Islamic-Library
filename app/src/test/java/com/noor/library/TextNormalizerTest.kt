package com.noor.library

import com.noor.library.util.TextNormalizer
import org.junit.Assert.assertEquals
import org.junit.Test

class TextNormalizerTest {
    @Test fun yehAndKafAreUnified() {
        assertEquals(TextNormalizer.normalize("كتاب"), TextNormalizer.normalize("کتاب"))
        assertEquals(TextNormalizer.normalize("علي"), TextNormalizer.normalize("علی"))
    }

    @Test fun harakatAreRemoved() {
        assertEquals("بسم الله", TextNormalizer.normalize("بِسْمِ ٱللَّهِ").replace("ٱ", "ا"))
    }

    @Test fun digitsAreConverted() {
        assertEquals("123", TextNormalizer.normalize("۱۲۳"))
        assertEquals("123", TextNormalizer.normalize("١٢٣"))
    }

    @Test fun persianDigitsForDisplay() {
        assertEquals("۱۱۴", TextNormalizer.toPersianDigits(114))
    }
}
