-- 반환 계약: 1=자기 잠금 삭제 성공, 0=소유권 불일치 또는 키 없음.
-- KEYS[1]=사용자 잠금 키, ARGV[1]=이 요청이 획득한 토큰.
-- 만료 후 새 요청이 획득한 키를 이전 요청이 삭제하지 않도록 한다.
if redis.call('get', KEYS[1]) == ARGV[1] then
    return redis.call('del', KEYS[1])
end
return 0
