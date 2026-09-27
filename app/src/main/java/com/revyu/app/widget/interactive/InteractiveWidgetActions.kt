package com.revyu.app.widget.interactive

object InteractiveWidgetActions {
    const val ACTION_FLIP = "com.revyu.app.action.PRACTICE_FLIP"
    const val ACTION_PREV = "com.revyu.app.action.PRACTICE_PREV"
    const val ACTION_NEXT = "com.revyu.app.action.PRACTICE_NEXT"
    const val ACTION_PICK_OPTION = "com.revyu.app.action.PRACTICE_PICK_OPTION"
    const val ACTION_CHECK = "com.revyu.app.action.PRACTICE_CHECK"
    const val ACTION_REVEAL = "com.revyu.app.action.PRACTICE_REVEAL"
    const val ACTION_SUBMIT = "com.revyu.app.action.PRACTICE_SUBMIT"
    const val ACTION_FINISH = "com.revyu.app.action.PRACTICE_FINISH"
    const val ACTION_RESTART = "com.revyu.app.action.PRACTICE_RESTART"
    const val ACTION_REBUILD = "com.revyu.app.action.PRACTICE_REBUILD"

    const val EXTRA_OPTION_INDEX = "practice_option_index"

    val INTERACTIVE_ACTIONS = setOf(
        ACTION_FLIP, ACTION_PREV, ACTION_NEXT, ACTION_PICK_OPTION,
        ACTION_CHECK, ACTION_REVEAL, ACTION_SUBMIT, ACTION_FINISH,
        ACTION_RESTART, ACTION_REBUILD
    )
}
