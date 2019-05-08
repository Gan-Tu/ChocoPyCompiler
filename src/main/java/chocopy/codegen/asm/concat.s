# Runtime support function concat.s

# Emit list concatenation code routine.
# --------------------------------------
# This routine behaves as a function.
# To concatenate two lists, 4 arguments are passed:
# - the address of boxing routine for elements of first list
# - the address of boxing routine for elements of second list
# - the address of the two lists.
#
# Register A0, A1 are modified in the routine.
#

  addi sp, sp, -8                          # Reserve space for caller's return addr, control link
  sw fp, 0(sp)                             # saved caller's dynamic link
  sw ra, 4(sp)                             # saved caller's return addr
  addi fp, sp, 8                           # New FP is at old SP
  addi sp, sp, -20                         # Reserve space for old S1-S6 values
  sw s1, -12(fp)                           # Save S1 value
  sw s2, -16(fp)                           # Save S2 value
  sw s3, -20(fp)                           # Save S3 value
  sw s4, -24(fp)                           # Save S4 value
  sw s5, -28(fp)                           # Save S5 value
  lw s1, 4(fp)                             # Load pointer to first list
  lw s2, 0(fp)                             # Load pointer to second list
  beqz s1, concat_none_error               # Check first list is not None
  beqz s2, concat_none_error               # Check second list is not None
  lw a0, @.__len__(s1)                     # Load length of first list
  lw a1, @.__len__(s2)                     # Load length of second list
  add a1, a0, a1                           # Calculate length of concatenated list
  mv s5, a1                                # Save length of concatenated list
  la a0, $.list$prototype                  # Load address to list prototype
  addi a1, a1, @.__list_header_words__     # Total words needed for list object
  jal alloc2                               # Allocate new list object
  sw s5, @.__len__(a0)                     # set __len__ attribute
  mv s5, a0                                # Point S6 at concatenated list
  addi s3, s5, @.__elts__                  # Point S3 to the fist element in concatenated list object
  lw s2, @.__len__(s1)                     # Load length of first list
  addi s1, s1, @.__elts__                  # Point S1 to the first element of first list
  lw s4, 12(fp)                            # Load address to first list's element boxing routine
concat_init_list_1:                        # Append list items of first list
  beqz s2, concat_init_list_2_pointer      # No need to append elements if first list is empty
  lw a0, 0(s1)                             # Read current list item from first list
  jalr s4                                  # Call list element boxing routine
  sw a0, 0(s3)                             # Set current list item in concatenated list object
  addi s3, s3, 4                           # Point S3 to next list item in concatenated list
  addi s1, s1, 4                           # Point S1 to next list item in first list
  addi s2, s2, -1                          # Reduce counter: one less list item to append.
  bnez s2, concat_init_list_1              # If counter != 0, continue appending elements of first list.
concat_init_list_2_pointer:                # Prepare for appending elements of second list
  lw s1, 0(fp)                             # Load pointer to second list
  lw s2, @.__len__(s1)                     # Load length of second list
  addi s1, s1, @.__elts__                  # Point S1 to the first element of second list
  lw s4, 8(fp)                             # Load address to second list's element boxing routine
concat_init_list_2:                        # Append list items of second list
  beqz s2, concat_done                     # No need to append elements if second list is empty
  lw a0, 0(s1)                             # Read current list item from second list
  jalr s4                                  # Call list element boxing routine
  sw a0, 0(s3)                             # Set current list item in concatenated list object
  addi s3, s3, 4                           # Point S3 to next list item in concatenated list
  addi s1, s1, 4                           # Point S2 to next list item in second list
  addi s2, s2, -1                          # Reduce counter: one less list item to append.
  bnez s2, concat_init_list_2              # If counter != 0, continue appending elements of second list.
concat_done:                               # List concatenation done.
  mv a0, s5                                # Point A0 at concatenated list
  lw s5, -24(fp)                           # Restore old S5 value
  lw s4, -20(fp)                           # Restore old S4 value
  lw s3, -16(fp)                           # Restore old S3 value
  lw s2, -12(fp)                           # Restore old S2 value
  lw s1, -8(fp)                            # Restore old S1 value
  addi sp, sp, 20                          # Pop off old S1-S6 values
  lw ra, -4(fp)                            # Get return address
  mv t0, fp                                # load current FP/old SP address
  lw fp, -8(fp)                            # Use control link to restore caller's FP
  mv sp, t0                                # Restore old stack pointer
  jr ra                                    # Return to caller
concat_none_error:                         # Error: at least one list is None
  j error.None                             # Throw Operation on None error
