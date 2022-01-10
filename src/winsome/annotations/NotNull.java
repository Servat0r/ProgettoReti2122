package winsome.annotations;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE;

import java.lang.annotation.Documented;
import java.lang.annotation.Target;

/**
 * An annotation to indicate that the target Type/Field/Method/Parameter is never null.
 * @author Salvatore Correnti
 *
 */
@Documented
@Target({ TYPE, FIELD, METHOD, PARAMETER })
public @interface NotNull {

}
