_stock_completion() {
    local current previous action
    current="${COMP_WORDS[COMP_CWORD]}"
    previous="${COMP_WORDS[COMP_CWORD-1]}"
    action=""

    local actions="collect prepare train rank review buy sell check order clean pull push"
    local candidates=""

    for word in "${COMP_WORDS[@]}"; do
        case "$word" in
            collect|prepare|train|rank|review|buy|sell|check|order|clean|pull|push)
                action="$word"
                ;;
        esac
    done

    case "$previous" in
        --type)
            candidates="stock daily minute5 minute1"
            ;;
        --scope)
            candidates="market prepared model state"
            ;;
        *)
            if [[ -z "$action" ]]; then
                candidates="$actions"
            else
                case "$action" in
                    collect) candidates="--date --codes --type --model" ;;
                    prepare) candidates="--date --force --model" ;;
                    train) candidates="--force --model" ;;
                    rank|review) candidates="--date --model" ;;
                    buy|sell) candidates="--model" ;;
                    order|clean) candidates="--date" ;;
                    pull) candidates="--scope" ;;
                    push) candidates="--scope --force" ;;
                esac
            fi
            ;;
    esac

    COMPREPLY=( $(compgen -W "$candidates" -- "$current") )
}
complete -F _stock_completion stock
