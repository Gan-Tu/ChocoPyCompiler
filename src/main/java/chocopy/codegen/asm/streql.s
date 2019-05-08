# Runtime support function streql.s

# Emit string comparison check code.
# --------------------------------------
# This routine behaves as a function.
# To compare two strings, 2 arguments are passed, each being
# the address of the two strings. A0 is 1 if strings are equal.
#
# Register A0, A1, T0, T1, T2, T3, T4 are modified in the routine.
#

  addi sp, sp, -8                          # Reserve space for caller's return addr, control link
  sw fp, 0(sp)                             # saved caller's dynamic link
  sw ra, 4(sp)                             # saved caller's return addr
  addi fp, sp, 8                           # New FP is at old SP
  lw a0, 4(fp)                             # Load pointer to first string
  lw a1, 0(fp)                             # Load pointer to second string
  beqz a0, streql_none                     # Check first string is not None
  beqz a1, streql_none                     # Check second string is not None
  lw t0, @.__len__(a0)                     # Load length of first string
  lw t1, @.__len__(a1)                     # Load length of second string
  bne t0, t1, streql_not_equal             # str1 != str2, if they differ in length
  addi t1, a0, @.__str__                   # Point T1 at first byte of first string
  addi t2, a1, @.__str__                   # Point T2 at first byte of second string
streql_byte_compare:                       # Compare byte by byte
  lbu t3, 0(t1)                            # Load current byte of first string
  lbu t4, 0(t2)                            # Load current byte of second string
  bne t3, t4, streql_not_equal             # str1 != str2, if one byte is different
  addi t1, t1, 1                           # Point T1 at next byte of first string
  addi t2, t2, 1                           # Point T2 at next byte of second string
  addi t0, t0, -1                          # Reduce counter: one less to compare
  bgtz t0, streql_byte_compare             # If counter != 0, continue byte comparison.
  li a0, 1                                 # Load true literal
  j streql_done                            # str1 == str2
streql_not_equal:                          # str1 != str2
  mv a0, zero                              # load false literal
streql_done:                               # String comparison done
  lw ra, -4(fp)                            # Get return address
  mv t0, fp                                # load current FP/old SP address
  lw fp, -8(fp)                            # Use control link to restore caller's FP
  mv sp, t0                                # Restore old stack pointer
  jr ra                                    # Return to caller
streql_none:                               # Error: at least one string is None
  j error.None                             # Throw Operation on None error
