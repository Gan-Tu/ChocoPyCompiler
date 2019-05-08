# Runtime support function conslist.s

# Emit list construction code routine.
# --------------------------------------
# This routine behaves as a function.
# To construct a list of N elements, N+1 parameters are passed
# as arguments. The first N arguments are the list values, and
# the last argument is the number of elements in the list.
#
# This routine requires `@.__list_header_words__` constant declared
# Register A0, A1, T0, T1, T2, T3 are modified in the routine.
#


  addi sp, sp, -8                          # Reserve space for caller's return addr, control link
  sw fp, 0(sp)                             # saved caller's dynamic link
  sw ra, 4(sp)                             # saved caller's return addr
  addi fp, sp, 8                           # New FP is at old SP
  la a0, $.list$prototype                  # Load address to list prototype
  lw a1, 0(fp)                             # Load list length
  beqz a1, conslist_done                   # Empty list can use list prototype
  addi a1, a1, @.__list_header_words__     # Total words needed for list object
  jal alloc2                               # allocate list object
  lw t0, 0(fp)                             # Load list length
  sw t0, @.__len__(a0)                     # set __len__ attribute
  slli t1, t0, 2                           # List length in bytes
  add t1, t1, fp                           # Set T1 to address of first argument
  addi t2, a0, @.__elts__                  # Set T2 to first list item in list object
conslist_initialization:                   # Initialize list items
  lw t3, 0(t1)                             # Read current list item from function arguments
  sw t3, 0(t2)                             # Set current list item in list object
  addi t1, t1, -4                          # Point T1 to address of next argument
  addi t2, t2, 4                           # Point T2 to address of next list item in list object
  addi t0, t0, -1                          # Reduce counter: one less list item to initialize.
  bnez t0, conslist_initialization         # If counter != 0, continue list initialization.
conslist_done:                             # List initialization done.
  lw ra, -4(fp)                            # Get return address
  mv t0, fp                                # load current FP/old SP address
  lw fp, -8(fp)                            # Use control link to restore caller's FP
  mv sp, t0                                # Restore old stack pointer
  jr ra                                    # Return to caller