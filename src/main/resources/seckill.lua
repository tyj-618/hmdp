-- KEYS[1] = 库存key
-- KEYS[2] = 订单用户集合key
-- KEYS[3] = Stream队列名

-- ARGV[1] = userId
-- ARGV[2] = voucherId
-- ARGV[3] = orderId

local stock = redis.call('get', KEYS[1])
if (not stock) then
	return 1
end

if (tonumber(stock) <= 0) then
	return 1
end

if (redis.call('sismember', KEYS[2], ARGV[1]) == 1) then
	return 2
end

-- 减扣库存
redis.call('incrby', KEYS[1], -1)

-- 记录用户下单
redis.call('sadd', KEYS[2], ARGV[1])

-- 发送订单消息到Stream
redis.call('xadd', KEYS[3], '*',
	'userId', ARGV[1],
	'voucherId', ARGV[2],
	'id', ARGV[3]
)

return 0