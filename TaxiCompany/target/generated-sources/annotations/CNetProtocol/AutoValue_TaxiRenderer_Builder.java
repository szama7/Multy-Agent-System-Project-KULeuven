
package CNetProtocol;

import javax.annotation.Generated;

@Generated("com.google.auto.value.processor.AutoValueProcessor")
 final class AutoValue_TaxiRenderer_Builder extends TaxiRenderer.Builder {

  private final TaxiRenderer.Language language;

  AutoValue_TaxiRenderer_Builder(
      TaxiRenderer.Language language) {
    if (language == null) {
      throw new NullPointerException("Null language");
    }
    this.language = language;
  }

  @Override
  TaxiRenderer.Language language() {
    return language;
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof TaxiRenderer.Builder) {
      TaxiRenderer.Builder that = (TaxiRenderer.Builder) o;
      return (this.language.equals(that.language()));
    }
    return false;
  }

  @Override
  public int hashCode() {
    int h = 1;
    h *= 1000003;
    h ^= this.language.hashCode();
    return h;
  }

  private static final long serialVersionUID = -1772420262312399129L;

}
