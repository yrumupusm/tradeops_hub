package io.tradeops.error;

public class OperationException extends RuntimeException {
  private final String code;
  private final int status;

  public OperationException(String code, int status) {
    super(code);
    this.code = code;
    this.status = status;
  }

  public String code() {
    return code;
  }

  public int status() {
    return status;
  }
}
