/*
 * Vendored from Jewel's org.jetbrains.jewel.ui.component.Scrollbar (v0.37.0-262.4852.51),
 * Apache License 2.0, Copyright JetBrains s.r.o.
 *
 * Why vendored: Jewel's public VerticalScrollbar only accepts a ScrollableState and builds its own
 * pixel adapter internally, with no hook to inject a custom one. We need the IDE-accurate look it
 * provides, plus drag coalescing for smooth fast dragging over a heavy list. The only change from
 * upstream is in SliderAdapter.onDragDelta (and onDragStarted): pointer moves fire several times per
 * frame and upstream queues a separate scrollTo (a full relayout) per delta, so on a long list the
 * backlog makes the thumb lag behind the cursor. Here the deltas are accumulated and a single worker
 * applies whatever has piled up since the last applied scroll, collapsing a burst into one scroll per
 * frame while staying exact (deltas are summed, never dropped). See [SliderAdapter.onDragDelta].
 */
@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.dv.loglens.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalDragOrCancellation
import androidx.compose.foundation.gestures.awaitVerticalDragOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.text.TextFieldScrollState
import androidx.compose.foundation.v2.ScrollbarAdapter
import androidx.compose.foundation.v2.maxScrollOffset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.SuspendingPointerInputModifierNode
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.jewel.foundation.modifier.thenIf
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.styling.ScrollbarStyle
import org.jetbrains.jewel.ui.component.styling.ScrollbarVisibility
import org.jetbrains.jewel.ui.component.styling.ScrollbarVisibility.AlwaysVisible
import org.jetbrains.jewel.ui.component.styling.TrackClickBehavior
import org.jetbrains.jewel.ui.component.styling.TrackClickBehavior.JumpToSpot
import org.jetbrains.jewel.ui.component.styling.TrackClickBehavior.NextPage
import org.jetbrains.jewel.ui.theme.scrollbarStyle

/**
 * A vertical scrollbar that can be tied to a [ScrollableState].
 *
 * @param scrollState The [ScrollableState] to control
 * @param modifier The modifier to apply to this layout node
 * @param reverseLayout `true` to reverse the direction of the scrollbar, `false` otherwise.
 * @param enabled `true` to enable the scrollbar, `false` otherwise.
 * @param interactionSource The [MutableInteractionSource] that will be used to dispatch events.
 * @param style The [ScrollbarStyle] to use for this scrollbar.
 * @param keepVisible `true` to keep the scrollbar visible even when not scrolling, `false` otherwise.
 */
@Composable
public fun VerticalScrollbar(
    scrollState: ScrollableState,
    modifier: Modifier = Modifier,
    reverseLayout: Boolean = false,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    style: ScrollbarStyle = JewelTheme.scrollbarStyle,
    keepVisible: Boolean = false,
) {
    BaseScrollbar(
        scrollState = scrollState,
        reverseLayout = reverseLayout,
        enabled = enabled,
        interactionSource = interactionSource,
        isVertical = true,
        style = style,
        keepVisible = keepVisible,
        modifier = modifier,
    )
}

/**
 * A horizontal scrollbar that can be tied to a [ScrollableState].
 *
 * @param scrollState The [ScrollableState] to control.
 * @param modifier The modifier to apply to this layout node.
 * @param reverseLayout `true` to reverse the direction of the scrollbar, `false` otherwise.
 * @param enabled `true` to enable the scrollbar, `false` otherwise.
 * @param interactionSource The [MutableInteractionSource] that will be used to dispatch events.
 * @param style The [ScrollbarStyle] to use for this scrollbar.
 * @param keepVisible `true` to keep the scrollbar visible even when not scrolling, `false` otherwise.
 */
@Composable
public fun HorizontalScrollbar(
    scrollState: ScrollableState,
    modifier: Modifier = Modifier,
    reverseLayout: Boolean = false,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    style: ScrollbarStyle = JewelTheme.scrollbarStyle,
    keepVisible: Boolean = false,
) {
    BaseScrollbar(
        scrollState = scrollState,
        reverseLayout = reverseLayout,
        enabled = enabled,
        interactionSource = interactionSource,
        isVertical = false,
        style = style,
        keepVisible = keepVisible,
        modifier = modifier,
    )
}

@Composable
private fun BaseScrollbar(
    scrollState: ScrollableState,
    reverseLayout: Boolean,
    enabled: Boolean,
    interactionSource: MutableInteractionSource,
    isVertical: Boolean,
    style: ScrollbarStyle,
    keepVisible: Boolean,
    modifier: Modifier = Modifier,
) {
    val isHovered by interactionSource.collectIsHoveredAsState()

    val dragInteraction = remember { mutableStateOf<DragInteraction.Start?>(null) }
    var showScrollbar by remember { mutableStateOf(false) }

    DisposableEffect(interactionSource) {
        onDispose {
            dragInteraction.value?.let { interaction ->
                interactionSource.tryEmit(DragInteraction.Cancel(interaction))
                dragInteraction.value = null
            }
        }
    }

    val visibilityStyle by rememberUpdatedState(style.scrollbarVisibility)
    val isOpaque by remember { derivedStateOf { visibilityStyle is AlwaysVisible } }

    val isDragging by remember { derivedStateOf { dragInteraction.value != null } }
    val isExpanded by remember { derivedStateOf { showScrollbar && (isHovered || isDragging) } }
    val isScrolling by remember { derivedStateOf { scrollState.isScrollInProgress || isDragging } }
    val isActive by remember { derivedStateOf { isOpaque || isScrolling || (keepVisible && showScrollbar) } }

    LaunchedEffect(isActive, isHovered, isDragging) {
        if (isActive || isHovered) {
            showScrollbar = true
        } else if (!isDragging) {
            launch {
                delay(visibilityStyle.lingerDuration)
                showScrollbar = false
            }
        }
    }

    val animatedThickness by
        animateDpAsState(
            if (isExpanded) visibilityStyle.trackThicknessExpanded else visibilityStyle.trackThickness,
            tween(visibilityStyle.expandAnimationDuration.inWholeMilliseconds.toInt(), easing = LinearEasing),
            "scrollbar_thickness",
        )

    val adapter =
        when (scrollState) {
            is LazyListState -> rememberScrollbarAdapter(scrollState)
            is LazyGridState -> rememberScrollbarAdapter(scrollState)
            is ScrollState -> rememberScrollbarAdapter(scrollState)
            is TextFieldScrollState -> rememberScrollbarAdapter(scrollState)
            else -> error("Unsupported scroll state type: ${scrollState::class.qualifiedName}")
        }

    with(LocalDensity.current) {
        var containerSize by remember { mutableIntStateOf(0) }
        val thumbMinHeight = style.metrics.minThumbLength.toPx()

        val coroutineScope = rememberCoroutineScope()
        val sliderAdapter =
            remember(adapter, scrollState, containerSize, thumbMinHeight, reverseLayout, isVertical, coroutineScope) {
                SliderAdapter(adapter, scrollState, containerSize, thumbMinHeight, reverseLayout, isVertical, coroutineScope)
            }

        val thumbBackgroundColor = getThumbBackgroundColor(isOpaque, isHovered, isScrolling, style, showScrollbar)
        val thumbBorderColor = getThumbBorderColor(isOpaque, isHovered, isScrolling, style, showScrollbar)
        val hasVisibleBorder = !areTheSameColor(thumbBackgroundColor, thumbBorderColor)
        val trackPadding by
            rememberUpdatedState(
                when {
                    isExpanded -> visibilityStyle.trackPaddingExpanded
                    hasVisibleBorder -> visibilityStyle.trackPaddingWithBorder
                    else -> visibilityStyle.trackPadding
                }
            )

        val thumbThicknessPx =
            if (isVertical) {
                    val layoutDirection = LocalLayoutDirection.current
                    animatedThickness -
                        trackPadding.calculateLeftPadding(layoutDirection) -
                        trackPadding.calculateRightPadding(layoutDirection)
                } else {
                    animatedThickness - trackPadding.calculateTopPadding() - trackPadding.calculateBottomPadding()
                }
                .roundToPx()

        val measurePolicy =
            if (isVertical) {
                remember(sliderAdapter, thumbThicknessPx) {
                    verticalMeasurePolicy(sliderAdapter, { containerSize = it }, thumbThicknessPx)
                }
            } else {
                remember(sliderAdapter, thumbThicknessPx) {
                    horizontalMeasurePolicy(sliderAdapter, { containerSize = it }, thumbThicknessPx)
                }
            }

        val canScroll = sliderAdapter.thumbSize < containerSize

        val trackBackground by
            animateColorAsState(
                targetValue = getTrackColor(isOpaque, isDragging, isHovered, style, isExpanded),
                animationSpec = trackColorTween(visibilityStyle),
                label = "scrollbar_trackBackground",
            )

        Layout(
            content = {
                Thumb(
                    showScrollbar,
                    visibilityStyle,
                    canScroll,
                    enabled,
                    interactionSource,
                    dragInteraction,
                    sliderAdapter,
                    thumbBackgroundColor,
                    thumbBorderColor,
                    hasVisibleBorder,
                    style.metrics.thumbCornerSize,
                )
            },
            modifier =
                modifier
                    .semantics { hideFromAccessibility() }
                    .thenIf(showScrollbar && canScroll && isExpanded) { background(trackBackground) }
                    .scrollable(
                        state = scrollState,
                        orientation = if (isVertical) Orientation.Vertical else Orientation.Horizontal,
                        enabled = enabled,
                        reverseDirection = true, // Not sure why it's needed, but it is — TODO revisit this
                    )
                    .padding(trackPadding)
                    .hoverable(interactionSource = interactionSource)
                    .thenIf(enabled && showScrollbar) {
                        scrollOnPressTrack(
                            style.trackClickBehavior,
                            isVertical,
                            reverseLayout,
                            sliderAdapter,
                            interactionSource,
                        )
                    },
            measurePolicy = measurePolicy,
        )
    }
}

private fun getTrackColor(
    isOpaque: Boolean,
    isDragging: Boolean,
    isHovered: Boolean,
    style: ScrollbarStyle,
    isExpanded: Boolean,
) =
    if (isOpaque) {
        if (isHovered || isDragging) {
            style.colors.trackOpaqueBackgroundHovered
        } else {
            style.colors.trackOpaqueBackground
        }
    } else {
        if (isExpanded) {
            style.colors.trackBackgroundExpanded
        } else {
            style.colors.trackBackground
        }
    }

private fun getThumbBackgroundColor(
    isOpaque: Boolean,
    isHovered: Boolean,
    isScrolling: Boolean,
    style: ScrollbarStyle,
    showScrollbar: Boolean,
) =
    if (isOpaque) {
        if (isHovered || isScrolling) {
            style.colors.thumbOpaqueBackgroundHovered
        } else {
            style.colors.thumbOpaqueBackground
        }
    } else {
        if (showScrollbar) {
            style.colors.thumbBackgroundActive
        } else {
            style.colors.thumbBackground
        }
    }

private fun getThumbBorderColor(
    isOpaque: Boolean,
    isHovered: Boolean,
    isScrolling: Boolean,
    style: ScrollbarStyle,
    showScrollbar: Boolean,
) =
    if (isOpaque) {
        if (isHovered || isScrolling) {
            style.colors.thumbOpaqueBorderHovered
        } else {
            style.colors.thumbOpaqueBorder
        }
    } else {
        if (showScrollbar) {
            style.colors.thumbBorderActive
        } else {
            style.colors.thumbBorder
        }
    }

private fun areTheSameColor(first: Color, second: Color) = first.toArgb() == second.toArgb()

@Suppress("MutableStateParam")
@Composable
private fun Thumb(
    showScrollbar: Boolean,
    visibilityStyle: ScrollbarVisibility,
    canScroll: Boolean,
    enabled: Boolean,
    interactionSource: MutableInteractionSource,
    dragInteraction: MutableState<DragInteraction.Start?>,
    sliderAdapter: SliderAdapter,
    thumbBackgroundColor: Color,
    thumbBorderColor: Color,
    hasVisibleBorder: Boolean,
    cornerSize: CornerSize,
) {
    val background by
        animateColorAsState(
            targetValue = thumbBackgroundColor,
            animationSpec = thumbColorTween(showScrollbar, visibilityStyle),
            label = "scrollbar_thumbBackground",
        )

    val border by
        animateColorAsState(
            targetValue = thumbBorderColor,
            animationSpec = thumbColorTween(showScrollbar, visibilityStyle),
            label = "scrollbar_thumbBorder",
        )

    val borderWidth = 1.dp
    val density = LocalDensity.current
    Box(
        Modifier.layoutId("thumb")
            .thenIf(canScroll) { drawThumb(background, borderWidth, border, hasVisibleBorder, cornerSize, density) }
            .thenIf(enabled) { scrollbarDrag(interactionSource, dragInteraction, sliderAdapter) }
    )
}

private fun Modifier.drawThumb(
    backgroundColor: Color,
    borderWidth: Dp,
    borderColor: Color,
    hasVisibleBorder: Boolean,
    cornerSize: CornerSize,
    density: Density,
) = drawBehind {
    val borderWidthPx = if (hasVisibleBorder) borderWidth.toPx() else 0f

    // First, draw the background, leaving room for the border around it
    val bgCornerRadius = CornerRadius((cornerSize.toPx(size, density) - borderWidthPx * 2).coerceAtLeast(0f))
    drawRoundRect(
        color = backgroundColor,
        topLeft = Offset(borderWidthPx, borderWidthPx),
        size = Size(size.width - borderWidthPx * 2, size.height - borderWidthPx * 2f),
        cornerRadius = bgCornerRadius,
    )

    // Then, draw the border itself
    if (hasVisibleBorder) {
        val strokeCornerRadius = CornerRadius(cornerSize.toPx(size, density))
        drawRoundRect(
            color = borderColor,
            topLeft = Offset(borderWidthPx / 2, borderWidthPx / 2),
            size = Size(size.width - borderWidthPx, size.height - borderWidthPx),
            cornerRadius = strokeCornerRadius,
            style = Stroke(borderWidthPx),
        )
    }
}

private fun trackColorTween(visibility: ScrollbarVisibility) =
    tween<Color>(visibility.trackColorAnimationDuration.inWholeMilliseconds.toInt(), easing = LinearEasing)

private fun thumbColorTween(showScrollbar: Boolean, visibility: ScrollbarVisibility) =
    tween<Color>(
        durationMillis =
            when {
                visibility is AlwaysVisible || !showScrollbar ->
                    visibility.thumbColorAnimationDuration.inWholeMilliseconds.toInt()

                else -> 0
            },
        delayMillis =
            when {
                visibility is AlwaysVisible && !showScrollbar -> visibility.lingerDuration.inWholeMilliseconds.toInt()
                else -> 0
            },
        easing = LinearEasing,
    )

// ===========================================================================
// Note: most of the code below is copied and adapted from the stock scrollbar
// ===========================================================================

private val SliderAdapter.thumbPixelRange: IntRange
    get() {
        val start = position.roundToInt()
        val endExclusive = start + thumbSize.roundToInt()

        return (start until endExclusive)
    }

private val IntRange.size
    get() = last + 1 - first

private fun verticalMeasurePolicy(sliderAdapter: SliderAdapter, setContainerSize: (Int) -> Unit, thumbThickness: Int) =
    MeasurePolicy { measurables, constraints ->
        setContainerSize(constraints.maxHeight)
        val pixelRange = sliderAdapter.thumbPixelRange
        val placeable =
            measurables.first().measure(Constraints.fixed(constraints.constrainWidth(thumbThickness), pixelRange.size))
        layout(placeable.width, constraints.maxHeight) { placeable.place(0, pixelRange.first) }
    }

private fun horizontalMeasurePolicy(
    sliderAdapter: SliderAdapter,
    setContainerSize: (Int) -> Unit,
    thumbThickness: Int,
) = MeasurePolicy { measurables, constraints ->
    setContainerSize(constraints.maxWidth)
    val pixelRange = sliderAdapter.thumbPixelRange
    val placeable =
        measurables.first().measure(Constraints.fixed(pixelRange.size, constraints.constrainHeight(thumbThickness)))
    layout(constraints.maxWidth, placeable.height) { placeable.place(pixelRange.first, 0) }
}

private fun Modifier.scrollbarDrag(
    interactionSource: MutableInteractionSource,
    draggedInteraction: MutableState<DragInteraction.Start?>,
    sliderAdapter: SliderAdapter,
): Modifier = this then ScrollbarDragModifier(interactionSource, draggedInteraction, sliderAdapter)

private fun Modifier.scrollOnPressTrack(
    clickBehavior: TrackClickBehavior,
    isVertical: Boolean,
    reverseLayout: Boolean,
    sliderAdapter: SliderAdapter,
    interactionSource: MutableInteractionSource,
) = this then ScrollOnPressTrackModifier(clickBehavior, isVertical, reverseLayout, sliderAdapter, interactionSource)

/** Responsible for scrolling when the scrollbar track is pressed (outside the thumb). */
private class TrackPressScroller(
    private val coroutineScope: CoroutineScope,
    private val sliderAdapter: SliderAdapter,
    private val reverseLayout: Boolean,
    private val clickBehavior: TrackClickBehavior,
) {
    /** The current direction of scroll (1: down/right, -1: up/left, 0: not scrolling) */
    private var direction = 0

    /** The currently pressed location (in pixels) on the scrollable axis. */
    private var offset: Float? = null

    /** The job that keeps scrolling while the track is pressed. */
    private var job: Job? = null

    /** Calculates the direction of scrolling towards the given offset (in pixels). */
    private fun directionOfScrollTowards(offset: Float): Int {
        val pixelRange = sliderAdapter.thumbPixelRange
        return when {
            offset < pixelRange.first -> if (reverseLayout) 1 else -1
            offset > pixelRange.last -> if (reverseLayout) -1 else 1
            else -> 0
        }
    }

    /** Scrolls once towards the current offset, if it matches the direction of the current gesture. */
    private suspend fun scrollTowardsCurrentOffset() {
        offset?.let {
            val currentDirection = directionOfScrollTowards(it)
            if (currentDirection != direction) {
                return
            }
            with(sliderAdapter.adapter) { scrollTo(scrollOffset + currentDirection * viewportSize) }
        }
    }

    /** Starts the job that scrolls continuously towards the current offset. */
    private fun startScrollingByPage() {
        job?.cancel()
        job =
            coroutineScope.launch {
                scrollTowardsCurrentOffset()
                delay(DELAY_BEFORE_SECOND_SCROLL_ON_TRACK_PRESS)
                while (true) {
                    scrollTowardsCurrentOffset()
                    delay(DELAY_BETWEEN_SCROLLS_ON_TRACK_PRESS)
                }
            }
    }

    /** Invoked on the first press for a gesture. */
    fun onPress(offset: Float) {
        this.offset = offset
        this.direction = directionOfScrollTowards(offset)

        if (direction == 0) return

        when (clickBehavior) {
            NextPage -> startScrollingByPage()
            JumpToSpot -> scrollToOffset(offset)
        }
    }

    /** Invoked when the pointer moves while pressed during the gesture. */
    fun onMovePressed(offset: Float) {
        this.offset = offset
        if (clickBehavior == JumpToSpot) scrollToOffset(offset)
    }

    /** Cleans up when the gesture finishes. */
    private fun cleanupAfterGesture() {
        job?.cancel()
        direction = 0
        offset = null
    }

    /** Invoked when the button is released. */
    fun onRelease() {
        cleanupAfterGesture()
    }

    private fun scrollToOffset(offset: Float) {
        job?.cancel()
        job =
            coroutineScope.launch {
                // Centre the thumb on the pressed spot and use the fast drag path (scrollBy near /
                // scrollToItem far) instead of upstream's adapter.scrollTo, which walks items.
                sliderAdapter.scrollToTrackOffset(offset.toDouble())
            }
    }

    /** Invoked when the gesture is cancelled. */
    fun onGestureCancelled() {
        cleanupAfterGesture()
        // Maybe revert to the initial position?
    }
}

/**
 * Detects the pointer events relevant for the "scroll by pressing on the track outside the thumb" gesture and calls the
 * corresponding methods in the [scroller].
 */
private suspend fun PointerInputScope.detectScrollViaTrackGestures(
    isVertical: Boolean,
    scroller: TrackPressScroller,
    interactionSource: MutableInteractionSource,
) {
    fun Offset.onScrollAxis() = if (isVertical) y else x

    awaitEachGesture {
        val down = awaitFirstDown()
        // Report the track gesture as a drag so observers of the interaction source (e.g. the rows'
        // scrub fast-path) treat press-and-move on the track like a thumb drag. Upstream emits nothing
        // here.
        val interaction = DragInteraction.Start()
        interactionSource.tryEmit(interaction)
        scroller.onPress(down.position.onScrollAxis())

        while (true) {
            val drag =
                if (isVertical) {
                    awaitVerticalDragOrCancellation(down.id)
                } else {
                    awaitHorizontalDragOrCancellation(down.id)
                }

            if (drag == null) {
                interactionSource.tryEmit(DragInteraction.Cancel(interaction))
                scroller.onGestureCancelled()
                break
            } else if (!drag.pressed) {
                interactionSource.tryEmit(DragInteraction.Stop(interaction))
                scroller.onRelease()
                break
            } else {
                scroller.onMovePressed(drag.position.onScrollAxis())
            }
        }
    }
}

/** The delay between the 1st and 2nd scroll while the scrollbar track is pressed outside the thumb. */
private const val DELAY_BEFORE_SECOND_SCROLL_ON_TRACK_PRESS: Long = 300L

/** The delay between each subsequent (after the 2nd) scroll while the scrollbar track is pressed outside the thumb. */
private const val DELAY_BETWEEN_SCROLLS_ON_TRACK_PRESS: Long = 100L

/**
 * Minimum gap between two applied thumb-drag scrolls. Slow/normal drags take the cheap incremental
 * scrollBy path (see [SliderAdapter.setPosition]) and don't need limiting, so this only paces the
 * expensive far jumps during a fast scrub; per-frame conflation (snapshotFlow) does the rest.
 * Higher = smoother thumb but chunkier content.
 */
private const val DRAG_SCROLL_THROTTLE_MS: Long = 16L

/**
 * How far (in viewports) the drag target may be from the current position and still scroll
 * incrementally via scrollBy — the smooth mouse-wheel path. Beyond this the drag jumps with
 * scrollToItem instead, since walking that many rows would be slower than composing a fresh viewport.
 */
private const val NEAR_DRAG_VIEWPORTS: Int = 3

@Immutable
private data class ScrollbarDragModifier(
    val interactionSource: MutableInteractionSource,
    val draggedInteraction: MutableState<DragInteraction.Start?>,
    val sliderAdapter: SliderAdapter,
) : ModifierNodeElement<ScrollbarDragNode>() {
    override fun create() = ScrollbarDragNode(interactionSource, draggedInteraction, sliderAdapter)

    override fun update(node: ScrollbarDragNode) {
        node.interactionSource = interactionSource
        node.draggedInteraction = draggedInteraction
        node.sliderAdapter = sliderAdapter
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "scrollbarDrag"
        properties["interactionSource"] = interactionSource
        properties["draggedInteraction"] = draggedInteraction
        properties["sliderAdapter"] = sliderAdapter
    }
}

private class ScrollbarDragNode(
    var interactionSource: MutableInteractionSource,
    var draggedInteraction: MutableState<DragInteraction.Start?>,
    var sliderAdapter: SliderAdapter,
) : DelegatingNode() {
    init {
        delegate(
            SuspendingPointerInputModifierNode {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val interaction = DragInteraction.Start()

                    interactionSource.tryEmit(interaction)
                    draggedInteraction.value = interaction
                    sliderAdapter.onDragStarted()

                    val isSuccess =
                        drag(down.id) { change ->
                            sliderAdapter.onDragDelta(change.positionChange())
                            change.consume()
                        }

                    sliderAdapter.onDragStopped()

                    val finishInteraction =
                        if (isSuccess) {
                            DragInteraction.Stop(interaction)
                        } else {
                            DragInteraction.Cancel(interaction)
                        }

                    interactionSource.tryEmit(finishInteraction)
                    draggedInteraction.value = null
                }
            }
        )
    }
}

@Immutable
private data class ScrollOnPressTrackModifier(
    val clickBehavior: TrackClickBehavior,
    val isVertical: Boolean,
    val reverseLayout: Boolean,
    val sliderAdapter: SliderAdapter,
    val interactionSource: MutableInteractionSource,
) : ModifierNodeElement<ScrollOnPressTrackNode>() {
    override fun create() =
        ScrollOnPressTrackNode(clickBehavior, isVertical, reverseLayout, sliderAdapter, interactionSource)

    override fun update(node: ScrollOnPressTrackNode) {
        node.update(clickBehavior, isVertical, reverseLayout, sliderAdapter, interactionSource)
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "scrollOnPressTrack"
        properties["clickBehavior"] = clickBehavior
        properties["isVertical"] = isVertical
        properties["reverseLayout"] = reverseLayout
        properties["sliderAdapter"] = sliderAdapter
        properties["interactionSource"] = interactionSource
    }
}

private class ScrollOnPressTrackNode(
    var clickBehavior: TrackClickBehavior,
    var isVertical: Boolean,
    var reverseLayout: Boolean,
    var sliderAdapter: SliderAdapter,
    var interactionSource: MutableInteractionSource,
) : DelegatingNode() {
    private val pointerInputNode =
        delegate(
            SuspendingPointerInputModifierNode {
                val scroller = TrackPressScroller(coroutineScope, sliderAdapter, reverseLayout, clickBehavior)

                detectScrollViaTrackGestures(
                    isVertical = isVertical,
                    scroller = scroller,
                    interactionSource = interactionSource,
                )
            }
        )

    fun update(
        clickBehavior: TrackClickBehavior,
        isVertical: Boolean,
        reverseLayout: Boolean,
        sliderAdapter: SliderAdapter,
        interactionSource: MutableInteractionSource,
    ) {
        val needsReset =
            this.clickBehavior != clickBehavior ||
                this.isVertical != isVertical ||
                this.reverseLayout != reverseLayout ||
                this.sliderAdapter != sliderAdapter ||
                this.interactionSource != interactionSource

        if (needsReset) {
            this.clickBehavior = clickBehavior
            this.isVertical = isVertical
            this.reverseLayout = reverseLayout
            this.sliderAdapter = sliderAdapter
            this.interactionSource = interactionSource

            // This cancels the current block and restarts it (recreating the scroller)
            // In other words, this `if` block is the "Key" equivalent
            pointerInputNode.resetPointerInputHandler()
        }
    }
}

internal class SliderAdapter(
    val adapter: ScrollbarAdapter,
    private val scrollState: ScrollableState,
    private val trackSize: Int,
    private val minHeight: Float,
    private val reverseLayout: Boolean,
    private val isVertical: Boolean,
    private val coroutineScope: CoroutineScope,
) {
    private val contentSize
        get() = adapter.contentSize

    private val visiblePart: Double
        get() {
            val contentSize = contentSize
            return if (contentSize == 0.0) {
                1.0
            } else {
                (adapter.viewportSize / contentSize).coerceAtMost(1.0)
            }
        }

    val thumbSize
        get() = (trackSize * visiblePart).coerceAtLeast(minHeight.toDouble())

    private val scrollScale: Double
        get() {
            val extraScrollbarSpace = trackSize - thumbSize
            val extraContentSpace = adapter.maxScrollOffset // == contentSize - viewportSize
            return if (extraContentSpace == 0.0) 1.0 else extraScrollbarSpace / extraContentSpace
        }

    private val rawPosition: Double
        get() = scrollScale * adapter.scrollOffset

    /** Thumb position (px in track) derived from the real scroll offset. */
    private val scrolledPosition: Double
        get() = if (reverseLayout) trackSize - thumbSize - rawPosition else rawPosition

    // While dragging, the thumb follows the pointer directly via [dragTarget] (snapshot state, so a
    // write remeasures the thumb immediately) — decoupled from the list scroll. Thumb tracking is
    // cheap and instant; the expensive list relayout catches up asynchronously and can lag without
    // dragging the thumb with it. Outside a drag it's null and the thumb tracks the real offset.
    private var dragTarget by mutableStateOf<Double?>(null)

    val position: Double
        get() = dragTarget ?: scrolledPosition

    val bounds
        get() = position..position + thumbSize

    // Latest thumb position the list should scroll to. Snapshot state so the scroll worker can
    // observe it via snapshotFlow, which conflates intermediate values — a burst of moves collapses
    // into one scroll.
    private var pendingScrollTarget by mutableStateOf(Double.NaN)
    private var scrollJob: Job? = null

    /** Called when the thumb dragging starts */
    fun onDragStarted() {
        dragTarget = scrolledPosition
        pendingScrollTarget = Double.NaN
        // Drive the list scroll from a separate coroutine, NOT inline in the pointer callback: the
        // scroll is a relayout on the UI thread, so running it inline would block the frame that
        // should draw the moved thumb (the lag). snapshotFlow conflates, so we only ever scroll to
        // the latest target, throttled.
        scrollJob?.cancel()
        scrollJob =
            coroutineScope.launch {
                var applied = Double.NaN
                snapshotFlow { pendingScrollTarget }
                    .collect { target ->
                        if (!target.isNaN() && target != applied) {
                            applied = target
                            setPosition(target)
                            if (DRAG_SCROLL_THROTTLE_MS > 0) delay(DRAG_SCROLL_THROTTLE_MS)
                        }
                    }
            }
    }

    /** Called when the thumb dragging ends — hand the thumb back to the real scroll offset. */
    fun onDragStopped() {
        scrollJob?.cancel()
        scrollJob = null
        dragTarget = null
    }

    /**
     * Scroll so the thumb centres on [trackOffset] (px on the track) — the JumpToSpot track-press
     * behaviour, routed through the same fast path as thumb dragging.
     */
    suspend fun scrollToTrackOffset(trackOffset: Double) {
        val maxThumb = (trackSize - thumbSize).coerceAtLeast(0.0)
        setPosition((trackOffset - thumbSize / 2).coerceIn(0.0, maxThumb))
    }

    private suspend fun setPosition(value: Double) {
        // [value] is the target thumb position in track space. On a LazyList, map it to an item index
        // and move there in one of two ways:
        //  - target close (within ~a couple of viewports): incremental scrollBy — the mouse-wheel
        //    path, which shifts the viewport and reuses composed rows, so a slow/normal drag is as
        //    smooth as wheel scrolling;
        //  - target far (fast scrub): scrollToItem, jumping straight to the index and composing only
        //    the destination viewport instead of walking/measuring everything in between.
        val state = scrollState
        val maxThumb = (trackSize - thumbSize).coerceAtLeast(1.0)
        val rawTarget = if (reverseLayout) trackSize - thumbSize - value else value
        val fraction = (rawTarget / maxThumb).coerceIn(0.0, 1.0)
        if (state is LazyListState) {
            val info = state.layoutInfo
            val total = info.totalItemsCount
            if (total > 0) {
                val exactIndex = fraction * (total - 1)
                val index = exactIndex.toInt()
                val items = info.visibleItemsInfo
                val estItemPx = if (items.isEmpty()) 0 else items.sumOf { it.size } / items.size
                val first = items.firstOrNull()
                if (first != null && estItemPx > 0) {
                    val hiddenTop = if (first.size <= 0) 0.0
                    else ((info.viewportStartOffset - first.offset).toDouble() / first.size).coerceIn(0.0, 1.0)
                    val currentExact = state.firstVisibleItemIndex + hiddenTop
                    val nearWindow = items.size * NEAR_DRAG_VIEWPORTS
                    if (kotlin.math.abs(exactIndex - currentExact) <= nearWindow) {
                        state.scrollBy(((exactIndex - currentExact) * estItemPx).toFloat())
                        return
                    }
                }
                state.scrollToItem(index, ((exactIndex - index) * estItemPx).toInt())
                return
            }
        }
        // Fallback for non-LazyList scroll states.
        adapter.scrollTo(rawTarget / scrollScale)
    }

    /**
     * Called on every movement while dragging the thumb. Cheap on purpose: it only writes two
     * snapshot states. The thumb tracks [dragTarget] immediately (instant remeasure), while the
     * scroll worker started in [onDragStarted] applies [pendingScrollTarget] asynchronously — so a
     * slow list relayout never holds back the thumb.
     */
    fun onDragDelta(offset: Offset) {
        val delta = if (isVertical) offset.y else offset.x
        val maxThumbPosition = (trackSize - thumbSize).coerceAtLeast(0.0)
        val target = ((dragTarget ?: scrolledPosition) + delta).coerceIn(0.0, maxThumbPosition)
        dragTarget = target
        pendingScrollTarget = target
    }
}
