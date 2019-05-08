# Runtime support function strcat.s

# Emit string concatenation code routine.
# --------------------------------------
# This routine behaves as a function.
# To concatenate two strings, 2 arguments are passed:
#    the address of the two strings
#
# Register A0, A1, T0, T1, T2, T3 are modified in the routine.
#

  addi sp, sp, -8                          # Reserve space for caller's return addr, control link
  sw fp, 0(sp)                             # saved caller's dynamic link
  sw ra, 4(sp)                             # saved caller's return addr
  addi fp, sp, 8                           # New FP is at old SP
  lw t0, 4(fp)                             # Load pointer to first string
  lw t1, 0(fp)                             # Load pointer to second string
  beqz t0, strcat_none_error               # Check first string is not None
  beqz t1, strcat_none_error               # Check second string is not None
  lw t0, @.__len__(t0)                     # Load length of first string
  beqz t0, strcat_return_second            # Return str_2, if str_1 is empty
  lw t1, @.__len__(t1)                     # Load length of second string
  beqz t1, strcat_return_first             # Return str_1, if str_2 is empty
  add t1, t0, t1                           # Calculate total length of concatenated string
  addi sp, sp, -4                          # Reserve stack space for total length
  sw t1, -12(fp)                           # Save total length of concatenated string on stack
  addi t1, t1, 4                           # Add 4 for ceiling operation
  srli t1, t1, 2                           # Convert length from bytes to words
  addi a1, t1, @.__string_header_words__   # Add header size
  la a0, $str$prototype                    # Load prototype to string object
  jal alloc2                               # Allocate new string object
  lw t0, -12(fp)                           # Retrieve total length of concatenated string
  addi sp, sp, 4                           # Pop total length of concatenated string off stack
  sw t0, @.__len__(a0)                     # Set attribute: __len__
  addi t2, a0, @.__str__                   # Point T2 at first byte in new string
  lw t0, 4(fp)                             # Point T0 at first string
  lw t1, @.__len__(t0)                     # Load length of first string, in bytes
  addi t0, t0, @.__str__                   # Point T0 at first byte in first string
strcat_append_first:                       # Append bytes of first string
  beqz t1, strcat_reset_pointer            # Done appending first string, if counter == 0
  lbu t3, 0(t0)                            # Load current byte from first string
  sb t3, 0(t2)                             # Save current byte to new string
  addi t1, t1, -1                          # Reduce counter: one less byte to append
  addi t0, t0, 1                           # Point T0 at next byte in first string
  addi t2, t2, 1                           # Point T2 at next byte in new string
  j strcat_append_first                    # Continue appending contents of first string
strcat_reset_pointer:                      # Prepare pointers for appending second string
  lw t0, 0(fp)                             # Point T0 at second string
  lw t1, @.__len__(t0)                     # Load length of second string, in bytes
  addi t0, t0, @.__str__                   # Point T0 at first byte in second string
strcat_append_second:                      # Prepare pointers for appending second string
  beqz t1, strcat_add_null                 # Done appending second string, if counter == 0
  lbu t3, 0(t0)                            # Load current byte from second string
  sb t3, 0(t2)                             # Save current byte to new string
  addi t1, t1, -1                          # Reduce counter: one less byte to append
  addi t0, t0, 1                           # Point T0 at next byte in second string
  addi t2, t2, 1                           # Point T2 at next byte in new string
  j strcat_append_second                   # Continue appending contents of second string
strcat_return_first:                       # special case: return first string
  lw a0, 4(fp)                             # Load pointer to first string
  j strcat_end                             # Go to end function epilogue
strcat_return_second:                      # special case: return second string
  lw a0, 0(fp)                             # Load pointer to second string
  j strcat_end                             # Go to end function epilogue
strcat_add_null::
  sb zero, 0(t2)                           # Append null byte to new string
strcat_end:                                # string concatenation done.
  lw ra, -4(fp)                            # Get return address
  mv t0, fp                                # load current FP/old SP address
  lw fp, -8(fp)                            # Use control link to restore caller's FP
  mv sp, t0                                # Restore old stack pointer
  jr ra                                    # Return to caller
strcat_none_error:                         # Error: at least one string is None
  j error.None                             # Throw Operation on None error

