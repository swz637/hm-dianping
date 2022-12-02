--优惠券id
local voucherId = ARGV[1]
--下单用户的id
--ARGV必须要大写
local userId = ARGV[2]

local stockKey = "seckill:stock:" .. voucherId
local oderKey = "seckill:order:" .. voucherId

--local stock = redis.call("get", stockKey)
--判断库存是否充足
if (tonumber(redis.call("get", stockKey)) <= 0) then
    --否，返回1
    return 1
end
--判断用户是否重复下单
--使用set存储类型，redis在oderKey表中存在时返回1
if (redis.call("sismember", oderKey, userId) == 1) then
    --是，返回2
    return 2
end
--否，扣库存，将用户添加到，对应的订单表
redis.call("incrby", stockKey, -1)
redis.call("sadd", oderKey, userId)
--返回0
return 0