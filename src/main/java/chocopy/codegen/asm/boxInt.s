# Runtime support function boxInt.s

# Turn integer value inside register $A0 to corresponding integer object.
# ---------------------------------------------------------------------------
# The caller has to ensure it's indeed a integer value, and is stored at $A0.
# Register $T0 is modified by this call.
#

  addi sp, sp, -8                          # Reserve space for caller's return addr, control link
  sw fp, 0(sp)                             # saved caller's dynamic link
  sw ra, 4(sp)                             # saved caller's return addr
  addi fp, sp, 8                           # New FP is at old SP
  sw a0, -12(fp)                           # Save integer value on stack before allocating new object
  la a0, $int$prototype                    # Load pointer to prototype of: int
  jal alloc                                # Allocate new object
  lw t0, -12(fp)                           # Load saved integer value from stack
  sw t0, @.__int__(a0)                     # Set attribute: __int__
  lw ra, -4(fp)                            # Get return address
  mv t0, fp                                # load current FP/old SP address
  lw fp, -8(fp)                            # Use control link to restore caller's FP
  mv sp, t0                                # Restore old stack pointer
  jr ra                                    # Return to caller