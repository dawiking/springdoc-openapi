package test.org.springdoc.api.app44;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

@RestController("/api")
public class HelloController {

	@PostMapping(value = "/helloworld", produces = "application/json", consumes = "application/vnd.v1+json")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Successful operation", content = @Content(schema = @Schema(implementation = HelloDTO1.class))),
			@ApiResponse(responseCode = "400", description = "Bad name", content = @Content(schema = @Schema(implementation = ErrorDTO.class))) })
	public ResponseEntity<?> hello(@RequestBody RequestV1 request) {
		final String name = request.getNameV1();
		if ("error".equalsIgnoreCase(name)) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorDTO("invalid name: " + name));
		}
		return ResponseEntity.ok(new HelloDTO1("Greetings from Spring Boot v1! " + name));
	}

	@PostMapping(value = "/helloworld", produces = "application/json", consumes = "application/vnd.v2+json")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Successful operation", content = @Content(schema = @Schema(implementation = HelloDTO2.class))),
			@ApiResponse(responseCode = "400", description = "Bad name", content = @Content(schema = @Schema(implementation = ErrorDTO.class))) })
	public ResponseEntity<?> hello(@RequestBody RequestV2 request) {
		final String name = request.getNameV2();
		if ("error".equalsIgnoreCase(name)) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorDTO("invalid name: " + name));
		}
		return ResponseEntity.ok(new HelloDTO2("Greetings from Spring Boot v2! " + name));
	}

	class HelloDTO1 {
		private String message;

		public HelloDTO1(String message) {
			this.message = message;
		}

		public String getMessage() {
			return message;
		}
	}

	class HelloDTO2 {
		private String message;

		public HelloDTO2(String message) {
			this.message = message;
		}

		public String getMessage() {
			return message;
		}
	}

	class ErrorDTO {
		private String errorMessage;

		public ErrorDTO(String errorMessage) {
			this.errorMessage = errorMessage;
		}

		public String getErrorMessage() {
			return errorMessage;
		}
	}

	static class RequestV1 {
		private String nameV1;

		public RequestV1() {
		}

		public String getNameV1() {
			return nameV1;
		}

		public void setNameV1(String nameV1) {
			this.nameV1 = nameV1;
		}
	}

	static class RequestV2 {
		private String nameV2;

		public RequestV2() {
		}

		public String getNameV2() {
			return nameV2;
		}

		public void setNameV2(String nameV2) {
			this.nameV2 = nameV2;
		}
	}

}