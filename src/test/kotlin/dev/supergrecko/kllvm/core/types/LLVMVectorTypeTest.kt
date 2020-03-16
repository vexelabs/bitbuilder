package dev.supergrecko.kllvm.core.types

import dev.supergrecko.kllvm.core.LLVMType
import dev.supergrecko.kllvm.core.enumerations.LLVMTypeKind
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class LLVMVectorTypeTest {
    @Test
    fun `underlying type matches`() {
        val type = LLVMType.makeInteger(32)
        val vec = LLVMType.makeVector(type, 10)

        assertEquals(10, vec.getSize())
        assertEquals(type.llvmType, vec.getElementType().llvmType)
    }
}