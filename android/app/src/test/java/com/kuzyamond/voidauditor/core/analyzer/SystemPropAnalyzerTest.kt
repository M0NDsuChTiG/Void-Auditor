package com.kuzyamond.voidauditor.core.analyzer

import com.kuzyamond.voidauditor.core.Capability
import com.kuzyamond.voidauditor.core.AuditIntelligence
import com.kuzyamond.voidauditor.core.RiskLevel
import com.kuzyamond.voidauditor.core.evidence.SystemPropEvidence
import com.kuzyamond.voidauditor.core.evidence.RawEvidence
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class SystemPropAnalyzerTest {

    private val analyzer = SystemPropAnalyzer()
    private val registry = DefaultAnalyzerRegistry.instance

    @Test
    fun testNonSystemPropCapabilityReturnsNull() {
        val capability = Capability.ReadUserIdentity
        val evidence = SystemPropEvidence(prop = "ro.build.type", value = "user")
        val intelligence = analyzer.analyze(capability, evidence)
        assertNull(intelligence)
    }

    @Test
    fun testNullEvidenceValueReturnsNull() {
        val capability = Capability.ReadSystemProp("ro.build.type")
        val evidence = SystemPropEvidence(prop = "ro.build.type", value = null)
        val intelligence = analyzer.analyze(capability, evidence)
        assertNull(intelligence)
    }

    @Test
    fun testUnknownPropertyReturnsNull() {
        val capability = Capability.ReadSystemProp("ro.unknown.prop")
        val evidence = SystemPropEvidence(prop = "ro.unknown.prop", value = "any")
        val intelligence = analyzer.analyze(capability, evidence)
        assertNull(intelligence)
    }

    @ParameterizedTest
    @MethodSource("systemPropCases")
    fun testSystemPropEvaluation(
        prop: String,
        value: String?,
        expectedRisk: RiskLevel?
    ) {
        val capability = Capability.ReadSystemProp(prop)
        val evidence = SystemPropEvidence(prop = prop, value = value)
        val intelligence = analyzer.analyze(capability, evidence)

        if (expectedRisk == null) {
            assertNull(intelligence)
        } else {
            assertNotNull(intelligence)
            assertEquals(expectedRisk, intelligence?.riskLevel)
            assertTrue(intelligence?.description?.contains(prop) == true)
            assertTrue(intelligence?.description?.contains(value ?: "") == true)
        }
    }

    @Test
    fun testRegistryRoutingSuccess() {
        val capability = Capability.ReadSystemProp("ro.build.type")
        val evidence = SystemPropEvidence(prop = "ro.build.type", value = "userdebug")
        val intelligence = registry.analyze(capability, evidence)

        assertNotNull(intelligence)
        assertEquals(RiskLevel.MEDIUM, intelligence?.riskLevel)
    }

    @Test
    fun testRegistryRoutingUnsupportedEvidenceReturnsNull() {
        val capability = Capability.ReadUserIdentity
        val evidence = RawEvidence(capabilityId = "ReadUserIdentity", output = "uid=0")
        val intelligence = registry.analyze(capability, evidence)
        assertNull(intelligence)
    }

    companion object {
        @JvmStatic
        fun systemPropCases(): Stream<Arguments> {
            return Stream.of(
                // ro.build.type
                Arguments.of("ro.build.type", "user", RiskLevel.LOW),
                Arguments.of("ro.build.type", "userdebug", RiskLevel.MEDIUM),
                Arguments.of("ro.build.type", "eng", RiskLevel.HIGH),
                Arguments.of("ro.build.type", "unknown_type", null),

                // ro.debuggable
                Arguments.of("ro.debuggable", "0", RiskLevel.LOW),
                Arguments.of("ro.debuggable", "1", RiskLevel.HIGH),
                Arguments.of("ro.debuggable", "2", null),

                // ro.secure
                Arguments.of("ro.secure", "1", RiskLevel.LOW),
                Arguments.of("ro.secure", "0", RiskLevel.HIGH),
                Arguments.of("ro.secure", "yes", null)
            )
        }
    }
}
