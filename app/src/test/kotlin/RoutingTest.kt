package diettracker

import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.bodyAsText
import io.ktor.client.request.get
import io.ktor.http.formUrlEncode
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

class RoutingTest{
    @Test
    fun should_return_ok_for_heath_check() = testApplication{
        application{
            routing{
                get("/health"){
                    call.respondText("OK")
                }
            }
        }
        val result = client.get("/health")
        assertEquals(200,result.status.value)
        assertEquals("OK", result.bodyAsText())
    }
    @Test
    fun should_load_landing_page() = testApplication{
        application{
            routing{
                get("/"){
                    call.respondText("OK")
                }
            }
        }
        val result = client.get("/")
        assertEquals(200,result.status.value)
        assertEquals("OK", result.bodyAsText())
    }
    @Test
    fun should_load_login_page() = testApplication{
        application{
            routing{
                get("/Login"){
                    call.respondText("OK")
                }
            }
        }
        val result = client.get("/Login")
        assertEquals(200,result.status.value)
        assertEquals("OK", result.bodyAsText())
    }
    @Test
    fun should_load_signup_page() = testApplication{
        application{
            routing{
                get("/Sign-Up"){
                    call.respondText("OK")
                }
            }
        }
        val result = client.get("/Sign-Up")
        assertEquals(200,result.status.value)
        assertEquals("OK", result.bodyAsText())
    }
    @Test
    fun should_login_user() = testApplication{
        application{
            routing{
                post("/Login"){
                    call.respondText("OK")
                }
            }
        }
        val result = client.submitForm(
            url = "/Login",
            formParameters = parameters{
                append("email","test@test.com")
                append("password","test")
            }
        )
        assertEquals(200,result.status.value)
        assertEquals("OK", result.bodyAsText())
    }
    @Test
    fun should_signup_user() = testApplication{
        application{
            routing{
                post("/Sign-Up"){
                    call.respondText("OK")
                }
            }
        }
        val result = client.submitForm(
            url = "/Sign-Up",
            formParameters = parameters{
                append("email","test@test.com")
                append("password","test")
            }
        )
        assertEquals(200,result.status.value)
        assertEquals("OK", result.bodyAsText())
    }
}
