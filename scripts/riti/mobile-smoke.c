#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "riti.h"

int main(void) {
    struct Config *config = riti_config_new();
    if (!riti_config_set_layout_file(config, "avro_phonetic")) return 1;
    riti_config_set_phonetic_suggestion(config, true);
    struct RitiContext *context = riti_context_new_with_config(config);
    riti_config_free(config);

    const uint16_t keys[] = { VC_A_SHIFT, VC_M, VC_I };
    struct Suggestion *suggestion = NULL;
    for (size_t i = 0; i < sizeof(keys) / sizeof(keys[0]); ++i) {
        riti_suggestion_free(suggestion);
        suggestion = riti_get_suggestion_for_key(context, keys[i], 0, 0);
    }

    char *text = riti_suggestion_get_pre_edit_text(suggestion, 0);
    if (text) puts(text);
    int status = text && strcmp(text, "আমি") == 0 ? 0 : 1;
    riti_string_free(text);
    riti_suggestion_free(suggestion);
    riti_context_finish_input_session(context);
    riti_context_free(context);
    return status;
}
