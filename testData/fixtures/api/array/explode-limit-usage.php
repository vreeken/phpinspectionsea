<?php

function cases_holder_positive_limit() {
    $skip = explode('/', '...');
    $target = <warning descr="[EA] 'explode('/', '...', 2)' could be used here (only some parts has been used).">explode('/', '...')</warning>;

    list($first,) = <warning descr="[EA] 'explode('/', '...', 2)' could be used here (only some parts has been used).">explode('/', '...')</warning>;
    list($first) = <warning descr="[EA] 'explode('/', '...', 2)' could be used here (only some parts has been used).">explode('/', '...')</warning>;
    list(, $second) = <warning descr="[EA] 'explode('/', '...', 3)' could be used here (only some parts has been used).">explode('/', '...')</warning>;
    list(, $second,) = <warning descr="[EA] 'explode('/', '...', 3)' could be used here (only some parts has been used).">explode('/', '...')</warning>;
    list($first, ,) = <warning descr="[EA] 'explode('/', '...', 2)' could be used here (only some parts has been used).">explode('/', '...')</warning>;

    return [
        <warning descr="[EA] 'explode('/', '...', 2)' could be used here (only some parts has been used).">explode('/', '...')</warning>[0],
        $target[0],

        explode('/', '...')[1],
        explode('/', '...'),
        $skip[1],

        current(<warning descr="[EA] 'explode('/', '...', 2)' could be used here (only some parts has been used).">explode('/', '...')</warning>),
    ];
}

function cases_holder_negative_limit($argument) {
    $parts = <warning descr="[EA] 'explode(':', $argument, -1)' could be used here (following 'array_pop(...)' call to be dropped then).">explode(':', $argument)</warning>;
    array_pop($parts);
    array_pop($parts);

    $fragments = <warning descr="[EA] 'explode(':', $argument, -2)' could be used here (following 'array_pop(...)' call to be dropped then).">explode(':', $argument, -1)</warning>;
    array_pop($fragments);

    return [$parts, $fragments];
}