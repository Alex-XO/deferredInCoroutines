import kotlinx.coroutines.*

class LazyDeferred<T>(
    private val block: suspend CoroutineScope.() -> T
) {
    @Volatile
    private var deferred: Deferred<T>? = null
    private val dependencies = mutableListOf<LazyDeferred<*>>()

    fun addDependency(dependency: LazyDeferred<*>) {
        dependencies.add(dependency)
    }

    fun start(scope: CoroutineScope): Deferred<T> {
        synchronized(this) {
            if (deferred == null) {
                val dependencyJobs = dependencies.map { it.start(scope) }
                deferred = scope.async(start = CoroutineStart.LAZY) {
                    dependencyJobs.awaitAll() // Await all dependencies
                    block()
                }
            }
            return deferred!!
        }
    }

    suspend fun await(scope: CoroutineScope): T {
        return start(scope).await()
    }
}

fun main() = runBlocking {
    val dependency1 = LazyDeferred {
        delay(1000)
        println("Dependency 1 completed")
        "Result 1"
    }

    val dependency2 = LazyDeferred {
        delay(1500)
        println("Dependency 2 completed")
        "Result 2"
    }

    val mainTask = LazyDeferred {
        val result1 = dependency1.await(this)
        val result2 = dependency2.await(this)
        println("Main task completed with results: $result1 and $result2")
        result1 + result2
    }

    mainTask.addDependency(dependency1)
    mainTask.addDependency(dependency2)

    val result = withTimeoutOrNull(4000) {
        mainTask.await(this)
    }

    if (result == null) {
        println("Main task timed out")
    } else {
        println("Final result: $result")
    }
}
