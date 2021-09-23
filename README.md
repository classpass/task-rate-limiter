# Overview

`TaskRateLimiter` provides a way to limit the rate of task execution.

Basic usage:

```kotlin
// TaskRateLimiter that allows executing 5 tasks per second
val taskRateLimiter = TaskRateLimiter(5, Duration.ofSeconds(1))

// Submit 10 tasks
(0 until 10).map { taskNum ->
    taskRateLimiter.submit {
        CompletableFuture.supplyAsync { println("Working on $taskNum at ${Instant.now()}") }
    }
}
```

At ClassPass, we use `TaskRateLimiter` to control the throughput of our requests to partner APIs.

```kotlin
val rateLimiter = TaskRateLimiter(requestsPerSecond, Duration.ofSeconds(1))

rateLimiter.submit {
    asyncHttpClient
        .prepareGet("example.com/endpoint/that/we/do/not/want/to/overload")
        .execute()
}
```

## See also

- [`TaskThrottle`](https://bitbucket.org/marshallpierce/task-throttle) which works similarly but limits task concurrency rather than throughput.
- Guava ['RateLimiter'](https://guava.dev/releases/30.1.1-jre/api/docs/com/google/common/util/concurrent/RateLimiter.html) provides a permit mechanism for implementing rate limiting
- [Throttle](https://github.com/comodal/throttle) is Guava's `RateLimiter` extracted to a separate project with different features

# Usage

```
implementation("com.classpass.oss.task-rate-limiter", "task-rate-limiter", "LATEST-VERSION-HERE")
```

# Contributing

We welcome contributions from everyone! See [CONTRIBUTING.md](CONTRIBUTING.md) for information on making a contribution.

# Development

## Formatting

The `check` task will check formatting (in addition to the other normal checks), and `formatKotlin` will auto-format.

## License headers

The `check` task will ensure that license headers are properly applied, and `licenseFormat` will apply headers for you.

# License

See [LICENSE](LICENSE) for the project license.
