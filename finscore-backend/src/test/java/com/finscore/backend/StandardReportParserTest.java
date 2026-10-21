package com.finscore.backend;

import com.finscore.backend.parser.StandardReportParser;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class StandardReportParserTest {
    @Test void parsesParenthesizedNegative() { assertEquals(new BigDecimal("-80.00"), StandardReportParser.parseNumber("(80.00)")); }
    @Test void parsesCommaNumber() { assertEquals(new BigDecimal("1250.00"), StandardReportParser.parseNumber("1,250.00")); }
}
