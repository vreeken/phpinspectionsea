<?php

function cases_holder() {
    return [
        in_array('...', []),
        in_array('...', array_values()),

        count([]),
        count(array_values()),

        str_replace(array_keys([]), [], '...'),
        str_replace(array_values([]), array_keys([]), '...'),
    ];
}