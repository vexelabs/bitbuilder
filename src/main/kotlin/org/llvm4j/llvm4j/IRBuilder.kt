package org.llvm4j.llvm4j

import org.bytedeco.llvm.LLVM.LLVMBuilderRef
import org.bytedeco.llvm.global.LLVM
import org.llvm4j.llvm4j.util.Owner
import org.llvm4j.llvm4j.util.toInt
import org.llvm4j.llvm4j.util.toPointerPointer
import org.llvm4j.optional.None
import org.llvm4j.optional.Option
import org.llvm4j.optional.Some

/**
 * LLVM IR Builder interface for generating LLVM IR
 *
 * The instructions which are not implemented are the oddly specific `callbr` and exception handling ones:
 *
 * - callbr
 * - cleanuppad
 * - catchpad
 * - landingpad
 * - cleanupret
 * - catchret
 * - catchswitch
 * - resume
 * - invoke
 *
 * TODO: APIs - Implement the remaining instructions
 * TODO: Testing - Test debug/fpmathtags once metadata is done
 * TODO: Testing - Execute IRBuilderTest functions once ExecutionEngine is done
 * TODO: Testing - Test atomic instructions (fence, atomiccmpxchg, atomicrmw)
 *
 * @author Mats Larsen
 */
public class IRBuilder public constructor(ptr: LLVMBuilderRef) : Owner<LLVMBuilderRef> {
    public override val ref: LLVMBuilderRef = ptr

    /**
     * Move the builder's insertion point after the given [instruction] in a basic block, [label].
     */
    public fun position(label: BasicBlock, instruction: Instruction) {
        LLVM.LLVMPositionBuilder(ref, label.ref, instruction.ref)
    }

    /**
     * Move the builder's insertion point before the given [instruction]
     */
    public fun positionBefore(instruction: Instruction) {
        LLVM.LLVMPositionBuilderBefore(ref, instruction.ref)
    }

    /**
     * Move the builder's insertion point after the given [label]
     */
    public fun positionAfter(label: BasicBlock) {
        LLVM.LLVMPositionBuilderAtEnd(ref, label.ref)
    }

    /**
     * Get the basic bloc kof the insertion point for the builder
     *
     * Returns [None] if the insertion point was cleared using [clear] or is yet to be set.
     */
    public fun getInsertionBlock(): Option<BasicBlock> {
        val point = LLVM.LLVMGetInsertBlock(ref)

        return Option.of(point).map { BasicBlock(it) }
    }

    /**
     * Retrieve the current debug location, if set
     *
     * TODO: Research/DebugInfo - Find a more precise type to return (llvm::DebugLoc)
     */
    public fun getDebugLocation(): Option<Metadata> {
        val debugLocation = LLVM.LLVMGetCurrentDebugLocation2(ref)

        return Option.of(debugLocation).map { Metadata(it) }
    }

    /**
     * Set the current debug location
     *
     * TODO: Research/DebugInfo - Find a more precise type to return (llvm::DebugLoc)
     */
    public fun setDebugLocation(location: Metadata) {
        LLVM.LLVMSetCurrentDebugLocation2(ref, location.ref)
    }

    /**
     * Attempt to use the current debug location to set the debug location for the provided instruction.
     *
     * TODO: Research - Does this fail if there is no debug location?
     */
    public fun attachDebugLocation(instruction: Instruction) {
        LLVM.LLVMSetInstDebugLocation(ref, instruction.ref)
    }

    /**
     * Get the default floating-point math metadata
     *
     * TODO: Research - Can this type be narrowed down?
     */
    public fun getDefaultFPMathTag(): Option<Metadata> {
        val flags = LLVM.LLVMBuilderGetDefaultFPMathTag(ref)

        return Option.of(flags).map { Metadata(it) }
    }

    public fun setDefaultFPMathTag(flags: Metadata) {
        LLVM.LLVMBuilderSetDefaultFPMathTag(ref, flags.ref)
    }

    /**
     * Clears the insertion point of the builder
     */
    public fun clear() {
        LLVM.LLVMClearInsertionPosition(ref)
    }

    public override fun deallocate() {
        LLVM.LLVMDisposeBuilder(ref)
    }

    /**
     * Build a return instruction
     *
     * The `ret` instruction exits control flow from the current function, optionally with a value. If you wish to
     * return a value from the terminator, pass in a [value]. If no value is passed, a `ret void` is made.
     *
     * The return type must be a first class type. See https://llvm.org/docs/LangRef.html#t-firstclass
     *
     * @param value value to return, returns void if [None]
     */
    public fun buildReturn(value: Option<Value>): ReturnInstruction {
        val res = when (value) {
            is Some -> LLVM.LLVMBuildRet(ref, value.unwrap().ref)
            is None -> LLVM.LLVMBuildRetVoid(ref)
        }

        return ReturnInstruction(res)
    }

    /**
     * Build an unconditional branch instruction
     *
     * The `br` instruction is used to cause control flow transfer to a different label in the current function. This
     * is an unconditional jump, see [buildConditionalBranch] for conditional jumps
     *
     * @param label label to jump to
     */
    public fun buildBranch(label: BasicBlock): BranchInstruction {
        val res = LLVM.LLVMBuildBr(ref, label.ref)

        return BranchInstruction(res)
    }

    /**
     * Build a conditional branch instruction
     *
     * The `br` instruction is used to cause control flow transfer to a different label in the current function. This
     * overload consumes a condition and two blocks. The condition is used to decide where control flow will transfer.
     *
     * @param condition condition value, must be i1 and not undef or poison
     * @param isTrue    label to jump to if the condition is true
     * @param isFalse   label to jump to if the condition is false
     */
    public fun buildConditionalBranch(
        condition: Value,
        isTrue: BasicBlock,
        isFalse: BasicBlock
    ): BranchInstruction {
        val res = LLVM.LLVMBuildCondBr(ref, condition.ref, isTrue.ref, isFalse.ref)

        return BranchInstruction(res)
    }

    /**
     * Build a switch instruction
     *
     * The `switch` instruction selects a destination to transfer control flow to based on an integer comparison. The
     * instruction is a generalization of the branching instruction, allowing a branch to occur to one of many
     * possible destinations.
     *
     * The C API does not consume all the cases upon construction, instead we provide an expected amount of
     * destinations which LLVM will pre-allocate for optimization purposes. Cases can be appended to the returned
     * [SwitchInstruction] instance.
     *
     * @param condition     conditional integer value to compare
     * @param default       label to jump to if none of the cases match
     * @param expectedCases expected amount of switch cases to be appended
     */
    public fun buildSwitch(condition: Value, default: BasicBlock, expectedCases: Int): SwitchInstruction {
        val res = LLVM.LLVMBuildSwitch(ref, condition.ref, default.ref, expectedCases)

        return SwitchInstruction(res)
    }

    /**
     * Build an indirect branch instruction
     *
     * The `indirectbr` instruction implements an indirect branch to a label within the current function.
     *
     * The C API does not consume all the possible destinations upon construction, instead we provide an expected
     * amount of possible destinations which LLVM will pre-allocate for optimization purposes. Destinations can be
     * appended to the returned [IndirectBrInstruction] instance.
     *
     * @param address       label to jump to
     * @param expectedCases expected amount of possible destinations to be appended
     *
     * TODO: Research - Is BlockAddress the correct type or should we accept Value?
     */
    public fun buildIndirectBranch(address: BlockAddress, expectedCases: Int): IndirectBrInstruction {
        val res = LLVM.LLVMBuildIndirectBr(ref, address.ref, expectedCases)

        return IndirectBrInstruction(res)
    }

    /**
     * Build an unreachable instruction
     *
     * The `unreachable` instruction does not have any semantics, it is a terminator which informs the optimizer that a
     * portion of the code is not reachable. This may be used to indicate that the code after a no-return function
     * cannot be reached.
     */
    public fun buildUnreachable(): UnreachableInstruction {
        val res = LLVM.LLVMBuildUnreachable(ref)

        return UnreachableInstruction(res)
    }

    /**
     * Build a float negation instruction
     *
     * The `fneg` instruction negates a floating-point or a vector-of-floating-point operand
     *
     * The produced value is a copy of its operand with the sign bit flipped.
     *
     * @param self floating-point or vector-of-floating-point to negate
     * @param name optional name for the instruction
     */
    public fun buildFloatNeg(self: Value, name: Option<String>): Value {
        val res = LLVM.LLVMBuildFNeg(ref, self.ref, name.toNullable() ?: "")

        return Value(res)
    }

    /**
     * Build an addition instruction
     *
     * The `add` instruction adds two integer or vector-of-integer operands
     *
     * The [semantics] decide how LLVM should handle integer overflow. If a semantic rule is specified and the value
     * does overflow, a poison value is returned
     *
     * @param lhs       left hand side integer to add
     * @param rhs       right hand side integer to add
     * @param semantics wrapping semantics upon overflow
     * @param name      optional name for the instruction
     */
    public fun buildIntAdd(lhs: Value, rhs: Value, semantics: WrapSemantics, name: Option<String>): Value {
        val res = when (semantics) {
            WrapSemantics.NoUnsigned -> LLVM.LLVMBuildNUWAdd(ref, lhs.ref, rhs.ref, name.toNullable() ?: "")
            WrapSemantics.NoSigned -> LLVM.LLVMBuildNSWAdd(ref, lhs.ref, rhs.ref, name.toNullable() ?: "")
            WrapSemantics.Unspecified -> LLVM.LLVMBuildAdd(ref, lhs.ref, rhs.ref, name.toNullable() ?: "")
        }

        return Value(res)
    }

    /**
     * Build a floating-point addition instruction
     *
     * The `fadd` instruction adds two floating-point or vector-of-floating-point operands
     *
     * @param lhs left hand side floating-point
     * @param rhs right hand side floating-point to add to [lhs]
     * @param name optional name for the instruction
     */
    public fun buildFloatAdd(lhs: Value, rhs: Value, name: Option<String>): Value {
        val res = LLVM.LLVMBuildFAdd(ref, lhs.ref, rhs.ref, name.toNullable() ?: "")

        return Value(res)
    }

    /**
     * Build a subtraction instruction
     *
     * The `sub` instruction subtracts to integer or vector-of-integer operands
     *
     * The [semantics] decide how LLVM should handle integer overflow. If a semantic rule is specified and the value
     * does overflow, a poison value is returned
     *
     * @param lhs       integer to subtract from
     * @param rhs       how much to subtract from [lhs]
     * @param semantics wrapping semantics upon overflow
     * @param name      optional name for the instruction
     */
    public fun buildIntSub(lhs: Value, rhs: Value, semantics: WrapSemantics, name: Option<String>): Value {
        val res = when (semantics) {
            WrapSemantics.NoUnsigned -> LLVM.LLVMBuildNUWSub(ref, lhs.ref, rhs.ref, name.toNullable() ?: "")
            WrapSemantics.NoSigned -> LLVM.LLVMBuildNSWSub(ref, lhs.ref, rhs.ref, name.toNullable() ?: "")
            WrapSemantics.Unspecified -> LLVM.LLVMBuildSub(ref, lhs.ref, rhs.ref, name.toNullable() ?: "")
        }

        return Value(res)
    }

    /**
     * Build a floating-point subtraction instruction
     *
     * The `fsub` instruction subtracts two floating-point or vector-of-floating-point operands
     *
     * @param lhs  floating-point to subtract from
     * @param rhs  how much to subtract from [lhs]
     * @param name optional name for the instruction
     */
    public fun buildFloatSub(lhs: Value, rhs: Value, name: Option<String>): Value {
        val res = LLVM.LLVMBuildFSub(ref, lhs.ref, rhs.ref, name.toNullable() ?: "")

        return Value(res)
    }

    /**
     * Build a multiplication instruction
     *
     * The `mul` instruction multiplies two integer or vector-of-integer operands
     *
     * The [semantics] decide how LLVM should handle integer overflow. If a semantic rule is specified and the value
     * does overflow, a poison value is returned
     *
     * @param lhs       left hand side integer to multiply
     * @param rhs       right hand side integer to multiply
     * @param semantics wrapping semantics upon overflow
     * @param name      optional name for the instruction
     */
    public fun buildIntMul(lhs: Value, rhs: Value, semantics: WrapSemantics, name: Option<String>): Value {
        val res = when (semantics) {
            WrapSemantics.NoUnsigned -> LLVM.LLVMBuildNUWMul(ref, lhs.ref, rhs.ref, name.toNullable() ?: "")
            WrapSemantics.NoSigned -> LLVM.LLVMBuildNSWMul(ref, lhs.ref, rhs.ref, name.toNullable() ?: "")
            WrapSemantics.Unspecified -> LLVM.LLVMBuildMul(ref, lhs.ref, rhs.ref, name.toNullable() ?: "")
        }

        return Value(res)
    }

    /**
     * Build a floating-point multiplication instruction
     *
     * The `fmul` instruction multiplies two floating-point or vector-of-floating-point operands
     *
     * @param lhs  left hand side floating-point to multiply
     * @param rhs  right hand side floating-point to multiply
     * @param name optional name for the instruction
     */
    public fun buildFloatMul(lhs: Value, rhs: Value, name: Option<String>): Value {
        val res = LLVM.LLVMBuildFMul(ref, lhs.ref, rhs.ref, name.toNullable() ?: "")

        return Value(res)
    }

    /**
     * Build an unsigned integer division instruction
     *
     * The `udiv` instruction divides two integer or vector-of-integer operands. The `udiv` instruction yields the
     * unsigned quotient of the two operands. Signed division is done with [buildSignedDiv]
     *
     * @param dividend dividend integer value (value being divided)
     * @param divisor  divisor integer value (the number dividend is being divided by)
     * @param exact    use llvm "exact" division (see language reference)
     * @param name     optional name for the instruction
     */
    public fun buildUnsignedDiv(dividend: Value, divisor: Value, exact: Boolean, name: Option<String>): Value {
        val res = if (exact) {
            LLVM.LLVMBuildExactUDiv(ref, dividend.ref, divisor.ref, name.toNullable() ?: "")
        } else {
            LLVM.LLVMBuildUDiv(ref, dividend.ref, divisor.ref, name.toNullable() ?: "")
        }

        return Value(res)
    }

    /**
     * Build a signed integer division instruction
     *
     * The `sdiv` instruction divides the two integer or vector-of-integer operands. The `sdiv` instruction yields
     * the signed quotient of the two operands. Unsigned division is done with [buildUnsignedDiv]
     *
     * @param dividend dividend integer value (value being divided)
     * @param divisor  divisor integer value (the number dividend is being divided by)
     * @param exact    use llvm "exact" division (see language reference)
     * @param name     optional name for the instruction
     */
    public fun buildSignedDiv(dividend: Value, divisor: Value, exact: Boolean, name: Option<String>): Value {
        val res = if (exact) {
            LLVM.LLVMBuildExactSDiv(ref, dividend.ref, divisor.ref, name.toNullable() ?: "")
        } else {
            LLVM.LLVMBuildSDiv(ref, dividend.ref, divisor.ref, name.toNullable() ?: "")
        }

        return Value(res)
    }

    /**
     * Build a floating-point division instruction
     *
     * The `fdiv` instruction divides the two floating-point or vector-of-floating-point operands.
     *
     * @param dividend dividend floating-point value (value being divided)
     * @param divisor  divisor floating-point value (the number divided is being divided by)
     * @param name     optional name for the instruction
     */
    public fun buildFloatDiv(dividend: Value, divisor: Value, name: Option<String>): Value {
        val res = LLVM.LLVMBuildFDiv(ref, dividend.ref, divisor.ref, name.toNullable() ?: "")

        return Value(res)
    }

    /**
     * Build an unsigned integer remainder instruction
     *
     * The `urem` instruction returns the remainder from the unsigned division of its two integer or
     * vector-of-integer operands.
     *
     * @param dividend dividend integer value (value being divided)
     * @param divisor  divisor integer value (the number dividend is being divided by)
     * @param name     optional name for the instruction
     */
    public fun buildUnsignedRem(dividend: Value, divisor: Value, name: Option<String>): Value {
        val res = LLVM.LLVMBuildURem(ref, dividend.ref, divisor.ref, name.toNullable() ?: "")

        return Value(res)
    }

    /**
     * Build a signed integer remainder instruction
     *
     * The `srem` instruction returns the remainder from the signed division of its two integer or vector-of-integer
     * operands.
     *
     * @param dividend dividend integer value (value being divided)
     * @param divisor  divisor integer value (the number dividend is being divided by)
     * @param name     optional name for the instruction
     */
    public fun buildSignedRem(dividend: Value, divisor: Value, name: Option<String>): Value {
        val res = LLVM.LLVMBuildSRem(ref, dividend.ref, divisor.ref, name.toNullable() ?: "")

        return Value(res)
    }

    /**
     * Build a floating-point remainder instruction
     *
     * The `frem` instruction returns the remainder from the division of its floating-point or
     * vector-of-floating-point operands.
     *
     * @param dividend dividend floating-point value (value being divided)
     * @param divisor  divisor floating-point value (the number dividend is being divided by)
     * @param name     optional name for the instruction
     */
    public fun buildFloatRem(dividend: Value, divisor: Value, name: Option<String>): Value {
        val res = LLVM.LLVMBuildFRem(ref, dividend.ref, divisor.ref, name.toNullable() ?: "")

        return Value(res)
    }

    /**
     * Build a left shift instruction
     *
     * The `shl` instruction shifts its first integer or vector-of-integer operand to the left a specified number of
     * bits
     *
     * @param lhs  integer value to shift left
     * @param rhs  number of bits to shift [lhs] to the left
     * @param name optional name for the instruction
     */
    public fun buildLeftShift(lhs: Value, rhs: Value, name: Option<String>): Value {
        val res = LLVM.LLVMBuildShl(ref, lhs.ref, rhs.ref, name.toNullable() ?: "")

        return Value(res)
    }

    /**
     * Build a logical shift right instruction
     *
     * The `lshr` instruction logically shifts its first integer or vector-of-integer operand to the right a
     * specified number of bits with zero fill.
     *
     * @param lhs  integer value to logically shift right
     * @param rhs  number of bits to shift [lhs] to the right
     * @param name optional name for the instruction
     */
    public fun buildLogicalShiftRight(lhs: Value, rhs: Value, name: Option<String>): Value {
        val res = LLVM.LLVMBuildLShr(ref, lhs.ref, rhs.ref, name.toNullable() ?: "")

        return Value(res)
    }

    /**
     * Build an arithmetic shift right instruction
     *
     * The `ashr` instruction arithmetically shifts its first integer or vector-of-integer operand to the right a
     * specified number of bits with sign extension.
     *
     * @param lhs  integer value to arithmetically shift right
     * @param rhs  number of bits to shift [lhs] to the right
     * @param name optional name for the instruction
     */
    public fun buildArithmeticShiftRight(lhs: Value, rhs: Value, name: Option<String>): Value {
        val res = LLVM.LLVMBuildAShr(ref, lhs.ref, rhs.ref, name.toNullable() ?: "")

        return Value(res)
    }

    /**
     * Build a logical and instruction
     *
     * The `and` instruction returns the bitwise logical and of its two integer or vector-of-integer operands.
     *
     * @param lhs  left hand side integer
     * @param rhs  right hand side integer
     * @param name optional name for the instruction
     */
    public fun buildLogicalAnd(lhs: Value, rhs: Value, name: Option<String>): Value {
        val res = LLVM.LLVMBuildAnd(ref, lhs.ref, rhs.ref, name.toNullable() ?: "")

        return Value(res)
    }

    /**
     * Build a logical or instruction
     *
     * The `or` instruction returns the bitwise logical or of its two integer or vector-of-integer operands.
     *
     * @param lhs  left hand side integer
     * @param rhs  right hand side integer
     * @param name optional name for the instruction
     */
    public fun buildLogicalOr(lhs: Value, rhs: Value, name: Option<String>): Value {
        val res = LLVM.LLVMBuildOr(ref, lhs.ref, rhs.ref, name.toNullable() ?: "")

        return Value(res)
    }

    /**
     * Build a logical xor instruction
     *
     * The `xor` instruction returns the bitwise logical xor of its two integer or vector-of-integer operands.
     *
     * @param lhs  left hand side integer
     * @param rhs  right hand side integer
     * @param name optional name for the instruction
     */
    public fun buildLogicalXor(lhs: Value, rhs: Value, name: Option<String>): Value {
        val res = LLVM.LLVMBuildXor(ref, lhs.ref, rhs.ref, name.toNullable() ?: "")

        return Value(res)
    }

    /**
     * Build an extract element instruction
     *
     * The `extractelement` instruction extracts a single element from a vector at a specified index.
     *
     * @param vector value to extract an element from
     * @param index  index of element to extract
     * @param name   optional name for the instruction
     */
    public fun buildExtractElement(vector: Value, index: Value, name: Option<String>): Value {
        val res = LLVM.LLVMBuildExtractElement(ref, vector.ref, index.ref, name.toNullable() ?: "")

        return Value(res)
    }

    /**
     * Build an insert element instruction
     *
     * The `insertelement` instruction inserts a single element into a vector at a specified index.
     *
     * @param vector value to insert an element into
     * @param value  the item to insert into the vector
     * @param index  the index to store the element
     * @param name   optional name for the instruction
     */
    public fun buildInsertElement(vector: Value, value: Value, index: Value, name: Option<String>): Value {
        val res = LLVM.LLVMBuildInsertElement(ref, vector.ref, value.ref, index.ref, name.toNullable() ?: "")

        return Value(res)
    }

    /**
     * Build a shuffle vector instruction
     *
     * The `shufflevector` instruction constructs a permutation of elements from two input vectors, returning a
     * vector with the same element type as the input and length that is the same as the shuffle mask.
     *
     * @param op1  first vector operand
     * @param op2  second vector operand
     * @param mask the shuffle mask
     * @param name optional name for the instruction
     */
    public fun buildShuffleVector(op1: Value, op2: Value, mask: Value, name: Option<String>): Value {
        val res = LLVM.LLVMBuildShuffleVector(ref, op1.ref, op2.ref, mask.ref, name.toNullable() ?: "")

        return Value(res)
    }

    /**
     * Build an extract value instruction
     *
     * The `extractvalue` instruction extracts the value of a member field from an aggregate value.
     *
     * The LLVM C API only allows for a single index to be used.
     *
     * @param aggregate struct or array value to extract value from
     * @param index     index in [aggregate] to retrieve
     * @param name      optional name for the instruction
     */
    public fun buildExtractValue(aggregate: Value, index: Int, name: Option<String>): Value {
        val res = LLVM.LLVMBuildExtractValue(ref, aggregate.ref, index, name.toNullable() ?: "")

        return Value(res)
    }

    /**
     * Build an insert value instruction
     *
     * The `insertvalue` instruction sets the value of a member field of an aggregate value.
     *
     * The LLVM C API only allows for a single index to be used.
     *
     * @param aggregate struct or array value to insert value into
     * @param value     value to insert at index
     * @param index     index in [aggregate] to insert element into
     * @param name      optional name for the instruction
     */
    public fun buildInsertValue(aggregate: Value, value: Value, index: Int, name: Option<String>): Value {
        val res = LLVM.LLVMBuildInsertValue(ref, aggregate.ref, value.ref, index, name.toNullable() ?: "")

        return Value(res)
    }

    /**
     * Build an alloca instruction
     *
     * The `alloca` instruction allocates memory on the stack frame of the currently executing function. This pointer
     * is automatically freed once the function returns to its caller. This instruction is used in
     * conjunction with `load` and `store`.
     *
     * @param type type to allocate
     * @param name optional name for the instruction
     */
    public fun buildAlloca(type: Type, name: Option<String>): AllocaInstruction {
        val res = LLVM.LLVMBuildAlloca(ref, type.ref, name.toNullable() ?: "")

        return AllocaInstruction(res)
    }

    /**
     * Build a load instruction
     *
     * The `load` instruction reads from a pointer in memory. This instruction is used in
     * conjunction with `alloca` and `store`.
     *
     * @param ptr  pointer value to read from
     * @param name optional name for the instruction
     */
    public fun buildLoad(ptr: Value, name: Option<String>): LoadInstruction {
        val res = LLVM.LLVMBuildLoad(ref, ptr.ref, name.toNullable() ?: "")

        return LoadInstruction(res)
    }

    /**
     * Build a store instruction
     *
     * The `store` instruction writes to a pointer in memory. This instruction is used in
     * conjunction with `alloca` and `load`.
     *
     * TODO: Research - Why does this not require the type?
     *
     * @param ptr   pointer value to write to
     * @param value value to write to pointer
     */
    public fun buildStore(ptr: Value, value: Value): StoreInstruction {
        val res = LLVM.LLVMBuildStore(ref, value.ref, ptr.ref)

        return StoreInstruction(res)
    }

    /**
     * Build a fence instruction
     *
     * The `fence` instruction is used to introduce happens-before edges between operations.
     *
     * TODO: Testing - Test this
     */
    public fun buildFence(ordering: AtomicOrdering, singleThread: Boolean, name: Option<String>): FenceInstruction {
        val res = LLVM.LLVMBuildFence(ref, ordering.value, singleThread.toInt(), name.toNullable() ?: "")

        return FenceInstruction(res)
    }

    /**
     * Build a comparison exchange instruction
     *
     * The `cmpxchg` instruction is used to atomically modify memory. It loads a value in memory and compares it to a
     * given value. If these values are equal, it tries to store a new value into the memory.
     *
     * TODO: Testing - Test this
     */
    public fun buildCmpXchg(
        ptr: Value,
        comparison: Value,
        new: Value,
        successOrdering: AtomicOrdering,
        failureOrdering: AtomicOrdering,
        singleThread: Boolean
    ): AtomicCmpXchgInstruction {
        val res = LLVM.LLVMBuildAtomicCmpXchg(
            ref,
            ptr.ref,
            comparison.ref,
            new.ref,
            successOrdering.value,
            failureOrdering.value,
            singleThread.toInt()
        )

        return AtomicCmpXchgInstruction(res)
    }

    /**
     * Build an atomic rmw instruction
     *
     * The `atomicrmw` instruction is used to atomically modify memory?
     *
     * TODO: Testing - Test this
     */
    public fun buildAtomicRMW(
        op: AtomicRMWBinaryOperation,
        ptr: Value,
        value: Value,
        ordering: AtomicOrdering,
        singleThread: Boolean
    ): AtomicRMWInstruction {
        val res = LLVM.LLVMBuildAtomicRMW(ref, op.value, ptr.ref, value.ref, ordering.value, singleThread.toInt())

        return AtomicRMWInstruction(res)
    }

    /**
     * Build a get element pointer instruction
     *
     * The `getelementptr` instruction is used to calculate the address of a sub-element of an aggregate data
     * structure. This is just a calculation and it does not access memory.
     *
     * If [inBounds] is true, the instruction will yield a poison value if one of the following rules are violated:
     * See semantics for instruction: https://llvm.org/docs/LangRef.html#id233
     *
     * @param aggregate struct or array value to extract value from
     * @param indices   directions/indices in the aggregate value to navigate through to find wanted element
     * @param inBounds  whether the getelementptr is in bounds
     */
    public fun buildGetElementPtr(
        aggregate: Value,
        vararg indices: Value,
        name: Option<String>,
        inBounds: Boolean,
    ): Value {
        val indicesPtr = indices.map { it.ref }.toPointerPointer()
        val res = if (inBounds) {
            LLVM.LLVMBuildInBoundsGEP(ref, aggregate.ref, indicesPtr, indices.size, name.toNullable() ?: "")
        } else {
            LLVM.LLVMBuildGEP(ref, aggregate.ref, indicesPtr, indices.size, name.toNullable() ?: "")
        }
        indicesPtr.deallocate()

        return Value(res)
    }

    /**
     * Build an integer trunc instruction
     *
     * The `trunc` instruction truncates its integer or vector-of-integer operand to the provided type.
     *
     * The bit size of the operand's type must be larger than the bit size of the destination type. Equal sized types
     * are not allowed.
     *
     * @param op   integer value to truncate
     * @param type type to truncate down to
     * @param name optional name for the instruction
     */
    public fun buildIntTrunc(op: Value, type: IntegerType, name: Option<String>): Value {
        val res = LLVM.LLVMBuildTrunc(ref, op.ref, type.ref, name.toNullable() ?: "")

        return Value(res)
    }

    /**
     * Build a zero extension instruction
     *
     * The `zext` instruction zero extends its integer or vector-of-integer operand to the provided type.
     *
     * The bit size of the operand's type must be smaller than the bit size of the destination type.
     *
     * @param op   integer value to zero extend
     * @param type type to zero extend to
     * @param name optional name for the instruction
     */
    public fun buildZeroExt(op: Value, type: IntegerType, name: Option<String>): Value {
        val res = LLVM.LLVMBuildZExt(ref, op.ref, type.ref, name.toNullable() ?: "")

        return Value(res)
    }

    /**
     * Build a sign extension instruction
     *
     * The `sext` instruction sign extends its integer or vector-of-integer operand to the provided type.
     *
     * The bit size of the operand's type must be smaller than the bit size of the destination type.
     *
     * @param op   integer value to sign extend
     * @param type type to sign extend to
     * @param name optional name for the instruction
     */
    public fun buildSignExt(op: Value, type: IntegerType, name: Option<String>): Value {
        val res = LLVM.LLVMBuildSExt(ref, op.ref, type.ref, name.toNullable() ?: "")

        return Value(res)
    }

    /**
     * Build a floating-point trunc instruction
     *
     * The `fptrunc` instruction truncates its floating-point or vector-of-floating-point operand to the provided type.
     *
     * The size of the operand's type must be larger than the destination type. Equal sized types are not allowed.
     *
     * @param op   floating-point value to truncate
     * @param type type to truncate down to
     * @param name optional name for the instruction
     */
    public fun buildFloatTrunc(op: Value, type: FloatingPointType, name: Option<String>): Value {
        val res = LLVM.LLVMBuildFPTrunc(ref, op.ref, type.ref, name.toNullable() ?: "")

        return Value(res)
    }

    /**
     * Build a float extension instruction
     *
     * The `fpext` instruction casts a floating-point or vector-of-floating-point operand to the provided type.
     *
     * The size of the operand's type must be smaller than the destination type.
     *
     * @param op   floating-point value to extend
     * @param type the type to extend to
     * @param name optional name for the instruction
     */
    public fun buildFloatExt(op: Value, type: FloatingPointType, name: Option<String>): Value {
        val res = LLVM.LLVMBuildFPExt(ref, op.ref, type.ref, name.toNullable() ?: "")

        return Value(res)
    }

    /**
     * Build a float to unsigned int cast instruction
     *
     * The `fptoui` instruction converts a floating-point or a vector-of-floating-point operand to its unsigned
     * integer equivalent.
     *
     * @param op   floating-point value to cast
     * @param type integer type to cast to
     * @param name optional name for the instruction
     */
    public fun buildFloatToUnsigned(op: Value, type: IntegerType, name: Option<String>): Value {
        val res = LLVM.LLVMBuildFPToUI(ref, op.ref, type.ref, name.toNullable() ?: "")

        return Value(res)
    }

    /**
     * Build a float to signed int cast instruction
     *
     * The `fptosi` instruction converts a floating-point or a vector-of-floating-point operand to its signed integer
     * equivalent.
     *
     * @param op   floating-point value to cast
     * @param type integer type to cast to
     * @param name optional name for the instruction
     */
    public fun buildFloatToSigned(op: Value, type: IntegerType, name: Option<String>): Value {
        val res = LLVM.LLVMBuildFPToSI(ref, op.ref, type.ref, name.toNullable() ?: "")

        return Value(res)
    }

    /**
     * Build an unsigned int to float cast instruction
     *
     * The `uitofp` instruction converts an unsigned integer or vector-of-integer operand to the floating-point type
     * equivalent.
     *
     * @param op   integer value to cast
     * @param type floating-point type to cast to
     * @param name optional name for the instruction
     */
    public fun buildUnsignedToFloat(op: Value, type: FloatingPointType, name: Option<String>): Value {
        val res = LLVM.LLVMBuildUIToFP(ref, op.ref, type.ref, name.toNullable() ?: "")

        return Value(res)
    }

    /**
     * Build a signed int to float cast instruction
     *
     * The `sitofp` instruction converts a signed integer or vector-of-integer operand to the floating-point type
     * equivalent.
     *
     * @param op   integer value to cast
     * @param type floating-point type to cast to
     * @param name optional name for the instruction
     */
    public fun buildSignedToFloat(op: Value, type: FloatingPointType, name: Option<String>): Value {
        val res = LLVM.LLVMBuildSIToFP(ref, op.ref, type.ref, name.toNullable() ?: "")

        return Value(res)
    }

    /**
     * Build a pointer to int cast instruction
     *
     * The `ptrtoint` instruction converts a pointer or vector-of-pointer operand to the provided integer type.
     *
     * @param op   pointer to cast
     * @param type integer type to cast to
     * @param name optional name for the instruction
     */
    public fun buildPointerToInt(op: Value, type: IntegerType, name: Option<String>): Value {
        val res = LLVM.LLVMBuildPtrToInt(ref, op.ref, type.ref, name.toNullable() ?: "")

        return Value(res)
    }

    /**
     * Build a int to pointer cast instruction
     *
     * The `inttoptr` instruction converts an integer operand and casts it to the provided pointer type.
     *
     * @param op   integer to cast
     * @param type pointer type to cast to
     * @param name optional name for the instruction
     */
    public fun buildIntToPointer(op: Value, type: PointerType, name: Option<String>): Value {
        val res = LLVM.LLVMBuildIntToPtr(ref, op.ref, type.ref, name.toNullable() ?: "")

        return Value(res)
    }

    /**
     * Build a bit cast instruction
     *
     * The `bitcast` instruction converts its operand to the provided type without changing any bits.
     *
     * @param op   value to cast
     * @param type type to cast to
     * @param name optional name for the instruction
     */
    public fun buildBitCast(op: Value, type: Type, name: Option<String>): Value {
        val res = LLVM.LLVMBuildBitCast(ref, op.ref, type.ref, name.toNullable() ?: "")

        return Value(res)
    }

    /**
     * Build an address space cast instruction
     *
     * The `addrspacecast` instruction converts a pointer value with a type in address space A to a pointer type in
     * address space B which must have a different address space.
     *
     * @param op   pointer value to cast
     * @param type pointer type to cast address space cast into
     * @param name optional name for the instruction
     */
    public fun buildAddressSpaceCast(op: Value, type: PointerType, name: Option<String>): Value {
        val res = LLVM.LLVMBuildAddrSpaceCast(ref, op.ref, type.ref, name.toNullable() ?: "")

        return Value(res)
    }

    /**
     * Build an integer comparison instruction
     *
     * The `icmp` instruction returns a boolean (i1) value based on comparison of two integer, vector-of-integer,
     * pointer or vector-of-pointer operands.
     *
     * @param predicate comparison operator to use
     * @param lhs       left hand side of comparison
     * @param rhs       right hand side of comparison
     * @param name      optional name for the instruction
     */
    public fun buildIntCompare(predicate: IntPredicate, lhs: Value, rhs: Value, name: Option<String>): Value {
        val res = LLVM.LLVMBuildICmp(ref, predicate.value, lhs.ref, rhs.ref, name.toNullable() ?: "")

        return Value(res)
    }

    /**
     * Build a floating-point comparison instruction
     *
     * The `fcmp` instruction returns a boolean (i1) value based on comparison of two floating-point or
     * vector-of-floating-point operands.
     *
     * @param predicate comparison operator to use
     * @param lhs       left hand side of comparison
     * @param rhs       right hand side of comparison
     * @param name      optional name for the instruction
     */
    public fun buildFloatCompare(predicate: FloatPredicate, lhs: Value, rhs: Value, name: Option<String>): Value {
        val res = LLVM.LLVMBuildFCmp(ref, predicate.value, lhs.ref, rhs.ref, name.toNullable() ?: "")

        return Value(res)
    }

    /**
     * Build a phi instruction
     *
     * The `phi` instruction is used to implement the \phi node in SSA-form
     *
     * The C API does not consume all the cases upon construction, instead we provide an expected amount of
     * destinations which LLVM will pre-allocate for optimization purposes. Cases can be appended to the returned
     * [PhiInstruction] instance.
     *
     * @param type the expected resolving type
     * @param name optional name for the instruction
     */
    public fun buildPhi(type: Type, name: Option<String>): PhiInstruction {
        val res = LLVM.LLVMBuildPhi(ref, type.ref, name.toNullable() ?: "")

        return PhiInstruction(res)
    }

    /**
     * Build a select instruction
     *
     * The `select` instruction is used to pick a value based on a boolean condition. It is analogous to the ternary
     * operator in C. The condition is either a 1-bit integer or a vector of 1-bit integers
     *
     * @param condition boolean (i1) condition
     * @param isTrue    value to select if [condition] is true
     * @param isFalse   value to select if [condition] is false
     * @param name      optional name for the instruction
     */
    public fun buildSelect(condition: Value, isTrue: Value, isFalse: Value, name: Option<String>): Value {
        val res = LLVM.LLVMBuildSelect(ref, condition.ref, isTrue.ref, isFalse.ref, name.toNullable() ?: "")

        return Value(res)
    }

    /**
     * Build a freeze instruction
     *
     * The `freeze` instruction is used to stop propagation of an undef or poison value.
     *
     * @param op   poison or undef value
     * @param name optional name for the instruction
     *
     * TODO: Testing - Test this
     */
    public fun buildFreeze(op: Value, name: Option<String>): Value {
        val res = LLVM.LLVMBuildFreeze(ref, op.ref, name.toNullable() ?: "")

        return Value(res)
    }

    /**
     * Build a call instruction
     *
     * The `call` instruction invokes a control flow jump into another function.
     *
     * @param function  function to invoke
     * @param arguments list of arguments to pass into function
     * @param name      optional name for the instruction
     */
    public fun buildCall(function: Function, vararg arguments: Value, name: Option<String>): Value {
        val argsPtr = arguments.map { it.ref }.toPointerPointer()
        val res = LLVM.LLVMBuildCall(ref, function.ref, argsPtr, arguments.size, name.toNullable() ?: "")
        argsPtr.deallocate()

        return Value(res)
    }

    /**
     * Build a variadic arguments instruction
     *
     * The `va_arg` instruction is used to access arguments passed through as variadic. It's also used to implement
     * the va_arg macro in C. The va_arg instruction returns the current item in the list and increases the pointer.
     *
     * See the LLVM documentation for details regarding va_arg: https://llvm.org/docs/LangRef.html#int-varargs
     *
     * @param list va_arg list to access
     * @param type expected type of the current element
     * @param name optional name for the instruction
     *
     * TODO: Testing - Test this
     */
    public fun buildVAArg(list: Value, type: Type, name: Option<String>): Value {
        val res = LLVM.LLVMBuildVAArg(ref, list.ref, type.ref, name.toNullable() ?: "")

        return Value(res)
    }
}
