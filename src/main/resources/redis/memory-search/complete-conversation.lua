-- 반환 계약: 1=UPDATE_SUCCESS, 0=CONVERSATION_MISSING, 2=CONTEXT_CONFLICT,
-- 3=LOCK_OWNERSHIP_LOST(잠금이 만료됐거나 다른 요청이 소유함).
-- KEYS[1]=대화 키, KEYS[2]=사용자 잠금 키.
-- ARGV[1]=읽었던 JSON, ARGV[2]=새 JSON, ARGV[3]=TTL(ms), ARGV[4]=소유권 토큰.
-- 문맥과 잠금은 동일 사용자 해시 태그로 같은 Cluster 슬롯에 둔다.
if redis.call('exists', KEYS[1]) == 0 then
    return 0
end
-- 이 검사를 별도 GET으로 분리하면 갱신 직전 만료·교체 경합을 막지 못한다.
if redis.call('get', KEYS[2]) ~= ARGV[4] then
    return 3
end
if redis.call('hget', KEYS[1], 'schemaVersion') ~= '1' then
    return redis.error_reply('Unsupported conversation schema')
end
local current = redis.call('hget', KEYS[1], 'turns')
if not current then
    return redis.error_reply('Missing conversation turns')
end
if current ~= ARGV[1] then
    return 2
end
redis.call('hset', KEYS[1], 'turns', ARGV[2])
redis.call('pexpire', KEYS[1], ARGV[3])
return 1
