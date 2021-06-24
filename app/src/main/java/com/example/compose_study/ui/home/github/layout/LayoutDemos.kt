package com.example.compose_study.ui.home.github.layout


import com.example.compose_study.ui.home.demo.ComposableDemo
import com.example.compose_study.ui.home.demo.DemoCategory


val LayoutDemos = DemoCategory(
    "LayoutDemos",
    listOf(
        ComposableDemo("BoxExample") { BoxExample() },
        ComposableDemo("ConstraintLayoutDemo") { ConstraintLayoutDemo() },

        ComposableDemo("ColumnExample") { ColumnExample() },
        ComposableDemo("RowExample") { RowExample() },
        )
)