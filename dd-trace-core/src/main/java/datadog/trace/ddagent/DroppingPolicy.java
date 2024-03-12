package datadog.trace.ddagent;

public interface DroppingPolicy {

  DroppingPolicy DISABLED = new DisabledDroppingPolicy();

  boolean active();

  class DisabledDroppingPolicy implements DroppingPolicy {

    @Override
    public boolean active() {
      return false;
    }
  }
}
