package tests.core.issues

import com.twilio.guardrail.generators.Http4s
import com.twilio.guardrail.{ Context, Server, Servers }
import org.scalatest.{ FunSuite, Matchers }
import support.SwaggerSpecRunner

class Issue165 extends FunSuite with Matchers with SwaggerSpecRunner {

  import scala.meta._

  val swagger: String =
    s"""
       |swagger: '2.0'
       |host: petstore.swagger.io
       |paths:
       |  "/":
       |    get:
       |      x-jvm-package: store
       |      operationId: getRoot
       |      responses:
       |        200:
       |         description: description
       |  "/foo":
       |    get:
       |      x-jvm-package: store
       |      operationId: getFoo
       |      responses:
       |        200:
       |         description: description
       |  "/foo/":
       |    get:
       |      x-jvm-package: store
       |      operationId: getFooDir
       |      responses:
       |        200:
       |         description: description
       |""".stripMargin

  test("Ensure routes are generated") {
    val (_, _, Servers(Server(_, _, genHandler, genResource :: _) :: Nil, Nil)) = runSwaggerSpec(swagger)(Context.empty, Http4s)

    val handler  = q"""
      trait StoreHandler[F[_]] {
        def getRoot(respond: GetRootResponse.type)(): F[GetRootResponse]
        def getFoo(respond: GetFooResponse.type)(): F[GetFooResponse]
        def getFooDir(respond: GetFooDirResponse.type)(): F[GetFooDirResponse]
      }
    """
    val resource = q"""
      class StoreResource[F[_]](mapRoute: (String, Request[F], F[Response[F]]) => F[Response[F]] = (_: String, _: Request[F], r: F[Response[F]]) => r)(implicit F: Async[F]) extends Http4sDsl[F] {
        def routes(handler: StoreHandler[F]): HttpRoutes[F] = HttpRoutes.of {
          {
            case req @ GET -> Root => 
              mapRoute("getRoot", req, {
                handler.getRoot(GetRootResponse)() flatMap {
                  case GetRootResponse.Ok =>
                    F.pure(Response[F](status = org.http4s.Status.Ok))
                }  
              })
            case req @ GET -> Root / "foo" =>
              mapRoute("getFoo", req, {
                handler.getFoo(GetFooResponse)() flatMap {
                  case GetFooResponse.Ok =>
                    F.pure(Response[F](status = org.http4s.Status.Ok))
                }
              })
            case req @ GET -> Root / "foo" / "" =>
              mapRoute("getFooDir", req, {
                handler.getFooDir(GetFooDirResponse)() flatMap {
                  case GetFooDirResponse.Ok =>
                    F.pure(Response[F](status = org.http4s.Status.Ok))
              }
            })
          }
        }
      }
    """

    genHandler.structure shouldEqual handler.structure

    // Cause structure is slightly different but source code is the same the value converted to string and then parsed
    genResource.toString().parse[Stat].get.structure shouldEqual resource.structure
  }
}
