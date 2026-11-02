-- 반환 계약: 1=CREATE_SUCCESS(생성 성공), 0=CREATE_KEY_ALREADY_EXISTS(키 충돌).
-- KEYS[1]=대화 키, ARGV[1]=형식 버전, ARGV[2]=빈 문맥 JSON, ARGV[3]=TTL(ms).
-- 생성과 TTL을 한 번에 적용해 만료 없는 빈 대화가 남지 않도록 한다.
if redis.call('exists', KEYS[1]) == 1 then
    return 0
end
redis.call('hset', KEYS[1], 'schemaVersion', ARGV[1], 'turns', ARGV[2])
redis.call('pexpire', KEYS[1], ARGV[3])
return 1
