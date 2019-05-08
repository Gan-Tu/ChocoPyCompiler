# Runtime support function initchars.s

# Initialize one-character strings, using the routine in reference.
# This routine requires `allChars` label declared

  la a0, $str$prototype                    # Load prototype to string object
  lw t0, 0(a0)                             # Load type tag
  lw t1, 4(a0)                             # Load size in words
  lw t2, 8(a0)                             # Load pointer to dispatch table
  li t3, 1                                 # Load attribute __len__ for one-character string
  la a0, allChars                          # Load address to data table: allChars
  li t4, 256                               # load max character decimal value
  mv t5, zero                              # Initialize counter to 0
initchars_init:                            # Initialize single character string
  sw t0, 0(a0)                             # Set type tag
  sw t1, 4(a0)                             # Set size in words
  sw t2, 8(a0)                             # Set pointer to dispatch table
  sw t3, 12(a0)                            # Set __len__ attribute
  sw t5, 16(a0)                            # Set character value
  addi a0, a0, 20                          # Move pointer to next free space
  addi t5, t5, 1                           # Increment counter
  bne t4, t5, initchars_init               # Continue initializing, if counter <= max char
  jr ra                                    # Return to caller

