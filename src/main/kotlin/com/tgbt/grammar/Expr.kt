package com.tgbt.grammar

import com.tgbt.post.PostStats
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule

@Serializable
enum class ConditionalOperator(val testFun: (Int, Int) -> Boolean) {
    EQUAL(Int::equals),
    GREATER_OR_EQUAL({ a, b -> a >= b }),
    GREATER({ a, b -> a > b }),
    LOWER_OR_EQUAL({ a, b -> a <= b }),
    LOWER({ a, b -> a < b });

    fun test(a: Int, b: Int) = testFun(a, b)
}

@Serializable
sealed class Expr {
    abstract fun evaluate(stats: PostStats): Boolean
}

@Serializable
@SerialName("likes")
data class Likes(val operator: ConditionalOperator, val number: Int) : Expr() {
    override fun evaluate(stats: PostStats) = operator.test(stats.likes, number)
}

@Serializable
@SerialName("reposts")
data class Reposts(val operator: ConditionalOperator, val number: Int) : Expr() {
    override fun evaluate(stats: PostStats) = operator.test(stats.reposts, number)
}

@Serializable
@SerialName("comments")
data class Comments(val operator: ConditionalOperator, val number: Int) : Expr() {
    override fun evaluate(stats: PostStats) = operator.test(stats.comments, number)
}

@Serializable
@SerialName("views")
data class Views(val operator: ConditionalOperator, val number: Int) : Expr() {
    override fun evaluate(stats: PostStats) = operator.test(stats.views, number)
}

@Serializable
@SerialName("and_operator")
data class And(val l: Expr, val r: Expr) : Expr() {
    override fun evaluate(stats: PostStats) = l.evaluate(stats) && r.evaluate(stats)
}

@Serializable
@SerialName("or_operator")
data class Or(val l: Expr, val r: Expr) : Expr() {
    override fun evaluate(stats: PostStats) = l.evaluate(stats) || r.evaluate(stats)
}

@Serializable
@SerialName("not_operator")
data class Not(val o: Expr) : Expr() {
    override fun evaluate(stats: PostStats) = !o.evaluate(stats)
}

val exprModule = SerializersModule {
    polymorphic(Expr::class) {
        Likes::class with Likes.serializer()
        Reposts::class with Reposts.serializer()
        Comments::class with Comments.serializer()
        Views::class with Views.serializer()
        And::class with And.serializer()
        Or::class with Or.serializer()
        Not::class with Not.serializer()
    }
}
