-- 반환 계약: 1=UPDATE_SUCCESS, 0=CONVERSATION_MISSING, 2=CONTEXT_CONFLICT.
-- KEYS[1]=대화 키, ARGV[1]=읽었던 JSON, ARGV[2]=새 JSON, ARGV[3]=TTL(ms).
-- 종료·만료된 대화는 재생성하지 않는다.
if redis.call('exists', KEYS[1]) == 0 then
    return 0
end
if redis.call('hget', KEYS[1], 'schemaVersion') ~= '1' then
    return redis.error_reply('Unsupported conversation schema')
end
local current = redis.call('hget', KEYS[1], 'turns')
if not current then
    return redis.error_reply('Missing conversation turns')
end
-- JSON 문자열만 비교해 Long ID를 Lua 숫자로 변환하지 않는다.
if current ~= ARGV[1] then
    return 2
end
redis.call('hset', KEYS[1], 'turns', ARGV[2])
redis.call('pexpire', KEYS[1], ARGV[3])
return 1
