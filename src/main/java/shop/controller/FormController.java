package shop.controller;

import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import shop.exception.ApiException;

/**
 * Общее для контроллеров форм: выполнить действие и показать результат.
 * Успех — ?msg=..., ошибка — ?error=... (эти параметры читает layout.js).
 */
abstract class FormController {

    /** Действие; и при успехе, и при ошибке возвращаемся на одну и ту же страницу. */
    protected String result(RedirectAttributes redirect, String path, String msg, Runnable action) {
        return result(redirect, path, path, msg, action);
    }

    /** Действие; при успехе возвращаемся на okPath, при ошибке — на errPath. */
    protected String result(RedirectAttributes redirect, String okPath, String errPath, String msg, Runnable action) {
        try {
            action.run();
            if (msg != null) redirect.addAttribute("msg", msg);
            return "redirect:" + okPath;
        } catch (ApiException e) {
            redirect.addAttribute("error", e.getMessage());
            return "redirect:" + errPath;
        }
    }
}
