/**
 * 
 */
package winsome.annotations;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.LOCAL_VARIABLE;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PACKAGE;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE;

import java.lang.annotation.Documented;
import java.lang.annotation.Target;

@Documented
@Target({TYPE, FIELD, METHOD, PARAMETER, LOCAL_VARIABLE, PACKAGE})
/**
 * @author Amministratore
 *
 */
public @interface SingleUserVerified {

}
