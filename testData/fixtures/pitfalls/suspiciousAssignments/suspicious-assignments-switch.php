<?php

    switch ($x) {
        case 'a':
            $y = 0;
        case 'b':
            <error descr="[EA] Overrides value from a preceding case (perhaps a 'break' is missing there).">$y</error> = 1;
            break;
        case 'c':
            $z = 0;
        case 'd':
            list(<error descr="[EA] Overrides value from a preceding case (perhaps a 'break' is missing there).">$z</error>, $zz) = [0, 0];
            break;
        case 'e':
            list($z, $zz) = [0, 0];
        case 'f':
            <error descr="[EA] Overrides value from a preceding case (perhaps a 'break' is missing there).">$z</error> = 0;
            break;
        case 'g':
            $arr[] = $x;
            $arr[] = $y;
            break;
        case 'h':
            $format = '%s';
            $format = sprintf($format, $format);
            break;
    }

    switch ($x) {
        case 'a':
            $y = 0;
        case 'b':
            $y .= 0;
            break;

        case 'c':
            $y .= 0;
        case 'd':
            $y = 0;
            break;

        case 'e':
            $y = 0;
        case 'f':
            $y = trim($y);
            break;
    }