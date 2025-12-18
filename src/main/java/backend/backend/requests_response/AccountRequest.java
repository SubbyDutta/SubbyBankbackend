package backend.backend.requests_response;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AccountRequest {
    @NotNull
    private String username;
    @NotNull
    private String adhar;
    @NotNull
    private String pan;
    @NotNull
    private String type;
}
