  -- Redis runs this entire script atomically
  local key = KEYS[1]
  local limit = tonumber(ARGV[1])
  local refillInterval = tonumber(ARGV[2])
  local now = tonumber(ARGV[3])

  -- Get or create bucket
  local tokens = tonumber(redis.call('hget', key, 'tokens'))
  local lastRefill = tonumber(redis.call('hget', key, 'lastRefill'))

  if tokens == nil then
      tokens = limit
      lastRefill = now
  end

  -- Refill
  local elapsed = now - lastRefill
  local tokensToAdd = math.floor(elapsed / refillInterval)
  if tokensToAdd > 0 then
      tokens = math.min(tokens + tokensToAdd, limit)
      lastRefill = now
  end

  -- Check and consume
  if tokens > 0 then
      tokens = tokens - 1
      redis.call('hset', key, 'tokens', tokens)
      redis.call('hset', key, 'lastRefill', lastRefill)
      return 1  -- allowed
  else
      redis.call('hset', key, 'tokens', tokens)
      redis.call('hset', key, 'lastRefill', lastRefill)
      return 0  -- rejected
  end
