# Runtime support function boxBool.s

# Turn boolean value inside register $A0 to corresponding boolean object.
# ---------------------------------------------------------------------------
# The caller has to ensure it's indeed a boolean value, and is stored at $A0.
# Register $T0 is modified by this call.
#

  la t0, const_0                           # Fetch address of False object
  slli a0, a0, 4                           # Get offset of right bool object
  add a0, t0, a0                           # Get correct boolean object
  jr ra                                    # Return to caller