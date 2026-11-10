-- Redis key existence is the revocation signal; this value only marks the key.
local REVOCATION_MARKER_VALUE = '1'
local REVOKE_SESSION_SCRIPT_SUCCESS_RESULT = 1

redis.call('PSETEX', KEYS[1], ARGV[1], REVOCATION_MARKER_VALUE)
redis.call('PSETEX', KEYS[2], ARGV[2], REVOCATION_MARKER_VALUE)
return REVOKE_SESSION_SCRIPT_SUCCESS_RESULT
