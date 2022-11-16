package com.aviral.weatherservice;

import com.google.protobuf.Timestamp;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.examples.lib.METARRequest;
import net.devh.boot.grpc.examples.lib.METARResponse;
import net.devh.boot.grpc.examples.lib.METARServiceGrpc;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.ZonedDateTime;

@SpringBootApplication
public class WeatherServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(WeatherServiceApplication.class, args);
	}

	@Bean
	WebClient webClient() {
		return WebClient.create("https://avwx.rest/api");
	}
}

@GrpcService
class MetarService extends METARServiceGrpc.METARServiceImplBase {
	@Value("${avwx.token:No Valid Token}")
	String token;
	private final WebClient webClient;
	public MetarService(WebClient webClient) {
		this.webClient = webClient;
	}

	@Override
	public void getWeather(METARRequest request, StreamObserver<METARResponse> responseObserver) {
		var metar = webClient.get()
			.uri("/metar/{id}?token={token}", request.getId(), token)
			.retrieve()
			.bodyToMono(METAR.class)
			.log()
			.block();
		responseObserver.onNext(METARResponse.newBuilder()
						.setRaw(metar.raw())
						.setTimestamp(Timestamp.newBuilder()
								.setSeconds(metar.time().dt().toEpochSecond())
								.build())
				.build());
		responseObserver.onCompleted();
	}
}

@RestController
@RequestMapping("/")
class WeatherController {
	@Value("${avwx.token:No Valid Token}")
	String token;

	public WeatherController(WebClient webClient) {
		this.webClient = webClient;
	}

	private final WebClient webClient;

	@GetMapping
	public final Mono<METAR> retrieveDefaultMETAR() {
		return this.retrieveMETAR("KSTL");
	}

	@GetMapping("/metar/{id}")
	public final Mono<METAR> retrieveMETAR(@PathVariable String id) {
		return webClient.get()
				.uri("/metar/{id}?token={token}", id, token)
				.retrieve()
				.bodyToMono(METAR.class)
				.log();
	}

	@GetMapping("/taf/{id}")
	public final Mono<TAF> retrieveTAF(@PathVariable String id) {
		return webClient.get()
				.uri("/taf/{id}?token={token}", id, token)
				.retrieve()
				.bodyToMono(TAF.class)
				.log();
	}
}

record METAR(String raw, Time time) {}
record TAF(Time start_time, Time end_time, Iterable<Forecast> forecasts) {}
record Forecast(String raw, Time start_time, Time end_time, Visibility visibility, WindDirection wind_direction, WindSpeed wind_speed) {}
record Time(ZonedDateTime dt, String repr) {}
record Visibility(String repr) {}
record WindDirection(String repr) {}
record WindSpeed(String repr) {}
