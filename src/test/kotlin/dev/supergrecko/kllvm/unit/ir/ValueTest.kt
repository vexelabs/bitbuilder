package dev.supergrecko.kllvm.unit.ir

import dev.supergrecko.kllvm.ir.ValueKind
import dev.supergrecko.kllvm.ir.types.IntType
import dev.supergrecko.kllvm.ir.values.constants.ConstantInt
import dev.supergrecko.kllvm.utils.KLLVMTestCase
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

internal class ValueTest : KLLVMTestCase() {
    @Test
    fun `Creation of ConstAllOne type`() {
        val type = IntType(32)
        val value = type.getConstantAllOnes()

        assertEquals(ValueKind.ConstantInt, value.getValueKind())
    }

    @Test
    fun `Creation of ConstNull type`() {
        val type = IntType(32)
        val value = type.getConstantNull()

        assertEquals(ValueKind.ConstantInt, value.getValueKind())
        assertTrue { value.isNull() }
    }

    @Test
    fun `Creation of nullptr type`() {
        val type = IntType(32)
        val nullptr = type.getConstantNullPointer()

        assertEquals(ValueKind.ConstantPointerNull, nullptr.getValueKind())
        assertTrue { nullptr.isNull() }
    }

    @Test
    fun `Creation of undefined type`() {
        val type = IntType(1032)
        val undef = type.getConstantUndef()

        assertEquals(ValueKind.UndefValue, undef.getValueKind())
        assertTrue { undef.isUndef() }
    }

    @Test
    fun `Value's pulled type matches input type`() {
        val type = IntType(32)
        val value = ConstantInt(type, 1L, true)

        val valueType = value.getType()

        assertEquals(type.getTypeKind(), valueType.getTypeKind())
        assertEquals(type.getTypeWidth(), valueType.asIntType().getTypeWidth())
        assertTrue { value.isConstant() }
    }
}
