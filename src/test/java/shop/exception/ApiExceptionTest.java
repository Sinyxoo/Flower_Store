package shop.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Единая ошибка бизнес-логики с HTTP-кодом ответа. */
@DisplayName("ApiException — ошибки бизнес-логики")
class ApiExceptionTest {

    @Test
    @DisplayName("check() при выполненном условии ничего не бросает")
    void checkПриВыполненномУсловииНеБросает() {
        assertThatCode(() -> ApiException.check(true, "что-то не так")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("check() при невыполненном условии бросает ошибку 400")
    void checkПриНевыполненномУсловииБросает400() {
        assertThatThrownBy(() -> ApiException.check(false, "Укажите имя"))
                .isInstanceOf(ApiException.class)
                .hasMessage("Укажите имя")
                .extracting(e -> ((ApiException) e).status)
                .isEqualTo(400);
    }

    @Test
    @DisplayName("фабрики ошибок выставляют правильный HTTP-код")
    void фабрикиОшибокВыставляютПравильныйКод() {
        assertThat(ApiException.badRequest("a").status).isEqualTo(400);
        assertThat(ApiException.unauthorized("a").status).isEqualTo(401);
        assertThat(ApiException.forbidden("a").status).isEqualTo(403);
        assertThat(ApiException.notFound("a").status).isEqualTo(404);
        assertThat(ApiException.conflict("a").status).isEqualTo(409);
    }

    @Test
    @DisplayName("blank() считает пустой строкой null и пробелы")
    void blankСчитаетПустымNullИПробелы() {
        assertThat(ApiException.blank(null)).isTrue();
        assertThat(ApiException.blank("")).isTrue();
        assertThat(ApiException.blank("   ")).isTrue();
        assertThat(ApiException.blank("\t\n")).isTrue();
        assertThat(ApiException.blank("текст")).isFalse();
    }

    @Test
    @DisplayName("тексты ошибок одинаковые для всех слоёв")
    void текстыОшибокОдинаковые() {
        assertThat(ApiException.PRODUCT_NOT_FOUND).isEqualTo("Товар не найден");
        assertThat(ApiException.CATEGORY_NOT_FOUND).isEqualTo("Категория не найдена");
        assertThat(ApiException.ORDER_NOT_FOUND).isEqualTo("Заказ не найден");
    }

    @Test
    @DisplayName("сообщение ошибки сохраняется в исключении")
    void сообщениеОшибкиСохраняется() {
        ApiException error = ApiException.conflict("Недостаточно на складе: доступно 2 шт.");

        assertThat(error).hasMessage("Недостаточно на складе: доступно 2 шт.");
        assertThat(error.status).isEqualTo(409);
    }
}
