package datadog.trace.common.sampling

import datadog.trace.api.DDTags
import datadog.trace.common.writer.ListWriter
import datadog.trace.common.writer.LoggingWriter
import datadog.trace.core.test.DDCoreSpecification
import static datadog.trace.api.sampling.PrioritySampling.*
import static datadog.trace.api.sampling.SamplingMechanism.*

class ForcePrioritySamplerTest extends DDCoreSpecification {

  def writer = new ListWriter()

  def "force priority sampling"() {
    setup:
    def sampler = new ForcePrioritySampler(prioritySampling, samplingMechanism)
    def tracer = tracerBuilder().writer(writer).sampler(sampler).build()

    when:
    def span1 = tracer.buildSpan("test").start()
    sampler.setSamplingPriority(span1)

    then:
    span1.getSamplingPriority() == expectedSampling
    sampler.sample(span1)

    cleanup:
    tracer.close()

    where:
    prioritySampling | samplingMechanism | expectedSampling
    SAMPLER_KEEP     | DEFAULT           | SAMPLER_KEEP
    SAMPLER_DROP     | DEFAULT           | SAMPLER_DROP
    SAMPLER_KEEP     | AGENT_RATE        | SAMPLER_KEEP
    SAMPLER_DROP     | AGENT_RATE        | SAMPLER_DROP
    SAMPLER_KEEP     | REMOTE_AUTO_RATE  | SAMPLER_KEEP
    SAMPLER_DROP     | REMOTE_AUTO_RATE  | SAMPLER_DROP
  }

  def "sampling priority set"() {
    setup:
    def sampler = new ForcePrioritySampler(prioritySampling, samplingMechanism)
    def tracer = tracerBuilder().writer(writer).sampler(sampler).build()

    when:
    def span = tracer.buildSpan("test").start()

    then:
    span.getSamplingPriority() == null

    when:
    span.setTag(DDTags.SERVICE_NAME, "spock")

    then:
    span.finish()
    writer.waitForTraces(1)
    span.getSamplingPriority() == expectedSampling

    cleanup:
    tracer.close()

    where:
    prioritySampling | samplingMechanism | expectedSampling
    SAMPLER_KEEP     | DEFAULT           | SAMPLER_KEEP
    SAMPLER_DROP     | DEFAULT           | SAMPLER_DROP
  }

  def "setting forced tracing via tag"() {
    when:
    def sampler = new ForcePrioritySampler(SAMPLER_KEEP, DEFAULT)
    def tracer = tracerBuilder().writer(new LoggingWriter()).sampler(sampler).build()
    def span = tracer.buildSpan("root").start()
    if (tagName) {
      span.setTag(tagName, tagValue)
    }
    span.finish()

    then:
    span.getSamplingPriority() == expectedPriority

    cleanup:
    tracer.close()

    where:
    tagName       | tagValue | expectedPriority
    'manual.drop' | true     | USER_DROP
    'manual.keep' | true     | USER_KEEP
  }

  def "not setting forced tracing via tag or setting it wrong value not causing exception"() {
    setup:
    def sampler = new ForcePrioritySampler(SAMPLER_KEEP, DEFAULT)
    def tracer = tracerBuilder().writer(new LoggingWriter()).sampler(sampler).build()
    def span = tracer.buildSpan("root").start()
    if (tagName) {
      span.setTag(tagName, tagValue)
    }

    expect:
    span.getSamplingPriority() == null

    cleanup:
    span.finish()
    tracer.close()

    where:
    tagName       | tagValue
    // When no tag is set default to
    null          | null
    // Setting to not known value
    'manual.drop' | false
    'manual.keep' | false
    'manual.drop' | 1
    'manual.keep' | 1
  }
}
