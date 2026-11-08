package com.github.ljarka.picfort.ui.timeline.row

fun calculateRowsSizes(numberOfRows: Int, itemsCount: Int): CalculatedRows {
    var singleItemRows = itemsCount // number of rows equal number of items
    val doubleItemsRows = itemsCount / 2
    val quadrupleItemRows = itemsCount / 4

    val baseRowType = if (singleItemRows - numberOfRows <= 0) {
        BaseRowType.SINGLE
    } else if (doubleItemsRows - numberOfRows <= 0) {
        BaseRowType.DOUBLE
    } else {
        BaseRowType.QUADRUPLE
    }

    return when (baseRowType) {
        BaseRowType.SINGLE -> CalculatedRows(singleItemRowCount = singleItemRows)
        BaseRowType.DOUBLE -> {
            val difference = numberOfRows - doubleItemsRows

            CalculatedRows(
                singleItemRowCount = difference * 2,
                doubleItemRowCount = doubleItemsRows - difference
            )
        }

        BaseRowType.QUADRUPLE -> {
            val difference = numberOfRows - quadrupleItemRows
            val quadrupleRows = quadrupleItemRows - difference
            val doubleRows = difference * 2
            val singleRows =
                maxOf(itemsCount - (quadrupleRows * 4 + doubleRows * 2), 0)

            CalculatedRows(
                singleItemRowCount = singleRows,
                quadrupleItemRowCount = quadrupleItemRows - difference,
                doubleItemRowCount = difference * 2,
            )
        }
    }
}